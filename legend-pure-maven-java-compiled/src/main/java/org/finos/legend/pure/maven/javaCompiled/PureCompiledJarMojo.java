// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.pure.maven.javaCompiled;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.AbstractMojoExecutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.SetIterable;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.pure.configuration.PureRepositoriesExternal;
import org.finos.legend.pure.m3.serialization.filesystem.PureCodeStorage;
import org.finos.legend.pure.m3.serialization.filesystem.repository.CodeRepository;
import org.finos.legend.pure.m3.serialization.filesystem.repository.GenericCodeRepository;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.classpath.ClassLoaderCodeStorage;
import org.finos.legend.pure.m3.serialization.runtime.PureRuntime;
import org.finos.legend.pure.m3.serialization.runtime.PureRuntimeBuilder;
import org.finos.legend.pure.m3.serialization.runtime.cache.CacheState;
import org.finos.legend.pure.m3.serialization.runtime.cache.ClassLoaderPureGraphCache;
import org.finos.legend.pure.m4.exception.PureException;
import org.finos.legend.pure.runtime.java.compiled.compiler.PureJavaCompiler;
import org.finos.legend.pure.runtime.java.compiled.extension.CompiledExtensionLoader;
import org.finos.legend.pure.runtime.java.compiled.generation.Generate;
import org.finos.legend.pure.runtime.java.compiled.generation.JavaPackageAndImportBuilder;
import org.finos.legend.pure.runtime.java.compiled.generation.JavaStandaloneLibraryGenerator;
import org.finos.legend.pure.runtime.java.compiled.serialization.binary.DistributedBinaryGraphSerializer;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

@Mojo(name = "build-pure-compiled-jar")
public class PureCompiledJarMojo extends AbstractMojo
{
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(readonly = true, defaultValue = "${project.build.directory}")
    private File targetDirectory;

    @Parameter(readonly = true, defaultValue = "${project.build.outputDirectory}")
    private File classesDirectory;

    @Parameter
    private String[] repositories;

    @Parameter
    private String[] excludedRepositories;

    @Parameter
    private File[] extraRepositories;

    @Parameter(defaultValue = "true")
    private boolean generateMetadata;

    @Parameter(defaultValue = "false")
    private boolean useSingleDir;

    @Parameter(defaultValue = "false")
    private boolean generateSources;

    @Parameter(defaultValue = "true")
    private boolean compile;

    private long startNanos;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        this.startNanos = System.nanoTime();
        try
        {
            getLog().info("Generating Java Compiled JAR");
            getLog().info("  Requested repositories: " + Arrays.toString(this.repositories));
            getLog().info("  Excluded repositories: " + Arrays.toString(this.excludedRepositories));
            getLog().info("  Extra repositories: " + Arrays.toString(this.extraRepositories));

            if (this.extraRepositories != null)
            {
                PureRepositoriesExternal.addRepositories(ArrayIterate.collect(this.extraRepositories, GenericCodeRepository::build));
            }

            Generate generate = initializeWriteMetadataAndGenerate();
            if (this.compile)
            {
                compileJavaSources(generate);
            }
            long endNanos = System.nanoTime();
            getLog().info(String.format("  Finished building Pure compiled mode jar (%.6fs)", getDurationNanos(this.startNanos, endNanos)));
        }
        catch (AbstractMojoExecutionException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("error", e);
        }
    }

    private long startStep(String step)
    {
        getLog().info("  Beginning " + step);
        return System.nanoTime();
    }

    private void completeStep(String step, long stepStart)
    {
        long stepEnd = System.nanoTime();
        getLog().info(String.format("    Finished %s (%.6fs)", step, getDurationNanos(stepStart, stepEnd)));
    }

    private double getDurationNanos(long start, long end)
    {
        return (end - start) / 1_000_000_000.0;
    }

    private MojoExecutionException mojoException(Exception e, String step, long stepStart)
    {
        long failureTime = System.nanoTime();
        getLog().error(String.format("    Error %s (%.6fs)", step, getDurationNanos(stepStart, failureTime)), e);
        getLog().error(String.format("    FAILURE building Pure compiled mode jar (%.6fs)", getDurationNanos(this.startNanos, failureTime) / 1_000_000_000.0));
        return new MojoExecutionException("Error writing Pure compiled mode Java code and metadata", e);
    }

    private MutableList<CodeRepository> resolveRepositories()
    {
        MutableList<CodeRepository> selectedRepos = (this.repositories == null) ? PureRepositoriesExternal.repositories().toList() : ArrayIterate.collect(this.repositories, PureRepositoriesExternal::getRepository);
        if (this.excludedRepositories != null)
        {
            ImmutableSet<String> excludedReposSet = Sets.immutable.with(this.excludedRepositories);
            selectedRepos.removeIf(r -> excludedReposSet.contains(r.getName()));
        }
        getLog().info("  Repositories with resolved dependencies: " + selectedRepos);
        return selectedRepos;
    }

    private Generate initializeWriteMetadataAndGenerate() throws MojoExecutionException, MojoFailureException
    {
        PureRuntime runtime = initializeRuntime();
        if (this.generateMetadata)
        {
            writeDistributedMetadata(runtime);
        }
        return generateJavaSources(runtime);
    }

    private PureRuntime initializeRuntime() throws MojoExecutionException, MojoFailureException
    {
        MutableList<CodeRepository> resolvedRepositories = resolveRepositories();
        try
        {
            getLog().info("  Beginning Pure initialization");
            SetIterable<CodeRepository> repositoriesForCompilation = PureCodeStorage.getRepositoryDependencies(PureRepositoriesExternal.repositories(), resolvedRepositories);

            // Add the project output to the plugin classloader
            URL[] urlsForClassLoader = ListIterate.collect(this.project.getCompileClasspathElements(), mavenCompilePath ->
            {
                try
                {
                    return Paths.get(mavenCompilePath).toUri().toURL();
                }
                catch (MalformedURLException e)
                {
                    throw new RuntimeException(e);
                }
            }).toArray(new URL[0]);
            getLog().info("    Project classLoader URLs " + Arrays.toString(urlsForClassLoader));
            ClassLoader classLoader = new URLClassLoader(urlsForClassLoader, Thread.currentThread().getContextClassLoader());

            // Initialize from PAR files cache
            PureCodeStorage codeStorage = new PureCodeStorage(null, new ClassLoaderCodeStorage(classLoader, repositoriesForCompilation));
            ClassLoaderPureGraphCache graphCache = new ClassLoaderPureGraphCache(classLoader);
            PureRuntime runtime = new PureRuntimeBuilder(codeStorage).withCache(graphCache).setTransactionalByDefault(false).buildAndTryToInitializeFromCache();
            if (!runtime.isInitialized())
            {
                CacheState cacheState = graphCache.getCacheState();
                if (cacheState != null)
                {
                    String lastStackTrace = cacheState.getLastStackTrace();
                    if (lastStackTrace != null)
                    {
                        getLog().warn("    Cache initialization failure: " + lastStackTrace);
                    }
                }
                getLog().info("    Initialization from caches failed - compiling from scratch");
                runtime.reset();
                runtime.loadAndCompileCore();
                runtime.loadAndCompileSystem();
            }
            long endNanos = System.nanoTime();
            getLog().info(String.format("    Finished Pure initialization (%.6fs)", getDurationNanos(this.startNanos, endNanos)));
            return runtime;
        }
        catch (PureException e)
        {
            long endNanos = System.nanoTime();
            getLog().error(String.format("    Error initializing Pure (%.6fs)", getDurationNanos(this.startNanos, endNanos)), e);
            throw new MojoFailureException(e.getInfo(), e);
        }
        catch (Exception e)
        {
            long endNanos = System.nanoTime();
            getLog().error(String.format("    Error initializing Pure (%.6fs)", getDurationNanos(this.startNanos, endNanos)), e);
            throw new MojoExecutionException("Error initializing Pure", e);
        }
    }

    private void writeDistributedMetadata(PureRuntime runtime) throws MojoExecutionException
    {
        String writeMetadataStep = "writing distributed Pure metadata";
        long writeMetadataStart = startStep(writeMetadataStep);
        try
        {
            Path distributedMetadataDirectory = this.useSingleDir ? this.classesDirectory.toPath() : this.targetDirectory.toPath().resolve("metadata-distributed");
            getLog().info("    Distributed metadata output directory: " + distributedMetadataDirectory);
            DistributedBinaryGraphSerializer.serialize(runtime, distributedMetadataDirectory);
            completeStep(writeMetadataStep, writeMetadataStart);
        }
        catch (Exception e)
        {
            throw mojoException(e, writeMetadataStep, writeMetadataStart);
        }
    }

    private Generate generateJavaSources(PureRuntime runtime) throws MojoExecutionException
    {
        String generateStep = "Pure compiled mode Java code generation";
        long generateStart = startStep(generateStep);
        try
        {
            Path codegenDirectory;
            if (this.generateSources)
            {
                codegenDirectory = this.targetDirectory.toPath().resolve("generated-sources");
                getLog().info("    Codegen output directory: " + codegenDirectory);
            }
            else
            {
                codegenDirectory = null;
            }
            JavaStandaloneLibraryGenerator generator = JavaStandaloneLibraryGenerator.newGenerator(runtime, CompiledExtensionLoader.extensions(), false, JavaPackageAndImportBuilder.externalizablePackage());
            Generate generate = generator.generateOnly(this.generateSources, codegenDirectory);
            completeStep(generateStep, generateStart);
            return generate;
        }
        catch (Exception e)
        {
            throw mojoException(e, generateStep, generateStart);
        }
    }

    private void compileJavaSources(Generate generate) throws MojoExecutionException
    {
        String compilationStep = "Pure compiled mode Java code compilation";
        long compilationStart = startStep(compilationStep);

        PureJavaCompiler compiler;
        try
        {
            compiler = JavaStandaloneLibraryGenerator.compileOnly(generate.getJavaSources(), generate.getExternalizableSources(), false);
            completeStep(compilationStep, compilationStart);
        }
        catch (Exception e)
        {
            throw mojoException(e, compilationStep, compilationStart);
        }

        String writeClassFilesStep = "writing Pure compiled mode Java classes";
        long writeClassFilesStart = startStep(writeClassFilesStep);
        getLog().info("    Classes output directory: " + this.classesDirectory);
        try
        {
            compiler.writeClassJavaSources(this.classesDirectory.toPath());
            completeStep(writeClassFilesStep, writeClassFilesStart);
        }
        catch (Exception e)
        {
            throw mojoException(e, writeClassFilesStep, writeClassFilesStart);
        }
    }
}
