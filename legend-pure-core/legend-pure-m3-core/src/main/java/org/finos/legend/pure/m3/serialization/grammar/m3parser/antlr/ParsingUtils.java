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

package org.finos.legend.pure.m3.serialization.grammar.m3parser.antlr;

import org.antlr.v4.runtime.RecognitionException;
import org.finos.legend.pure.m4.serialization.grammar.StringEscape;
import org.finos.legend.pure.m4.serialization.grammar.antlr.PureParserException;
import org.finos.legend.pure.m4.tools.TextTools;

public final class ParsingUtils
{

    public static StringBuilder removeLastCommaCharacterIfPresent(StringBuilder sb)
    {
        return sb.length() > 0 && sb.charAt(sb.length() - 1) == ',' ? sb.deleteCharAt(sb.length() - 1) : sb;
    }

    public static boolean isAntlrRecognitionExceptionUsingFastParser(boolean parseFast, Exception e)
    {
        return parseFast && e instanceof PureParserException && e.getCause() instanceof RecognitionException;
    }

    /**
     * Convert the raw text of a multi-line string literal ('''...''') into its value, following the Java
     * text-block algorithm: strip the opening delimiter line and the closing delimiter, remove the common
     * (incidental) leading-whitespace indentation, strip trailing whitespace from each line, then process
     * escape sequences. The opening '''  must be followed by a line terminator (enforced by the lexer), so
     * the first line of content is the line after it; the terminal newline is preserved iff the closing '''
     * sits on its own line.
     *
     * @param rawTokenText raw text of the multi-line string literal, delimiters included
     * @return string value of the literal
     * @throws IllegalArgumentException if the text is not delimited by ''' or the opening delimiter is not
     *                                  followed by a line terminator
     */
    public static String processMultilineString(String rawTokenText)
    {
        // Verify we have a valid multi-line string and split lines. We will replace line breaks uniformly
        // with \n.
        if (!((rawTokenText.length() >= 6) && rawTokenText.startsWith("'''") && rawTokenText.endsWith("'''")))
        {
            throw new IllegalArgumentException("Invalid multi-line string: " + rawTokenText);
        }
        String[] lines = rawTokenText.substring(3, rawTokenText.length() - 3).split("\r\n|\r|\n", -1);
        if ((lines.length == 0) || !TextTools.isBlank(lines[0]))
        {
            throw new IllegalArgumentException("Invalid multi-line string: " + rawTokenText);
        }

        // Minimum indentation is computed over every non-blank line plus the last line (the closing-delimiter
        // line, even when blank) - the latter sets a floor that prevents over-stripping.
        int minIndent = Integer.MAX_VALUE;
        for (int i = 1; (minIndent != 0) && (i < lines.length); i++)
        {
            String line = lines[i];
            int index = TextTools.indexOfNonWhitespace(line);
            if (index != -1)
            {
                minIndent = Math.min(minIndent, index);
            }
            else if (i == lines.length - 1)
            {
                minIndent = Math.min(minIndent, line.length());
            }
        }

        StringBuilder builder = new StringBuilder(rawTokenText.length() - 6 - lines[0].length());
        for (int i = 1; i < lines.length; i++)
        {
            if (i > 1)
            {
                builder.append('\n');
            }
            String line = lines[i];
            if (minIndent < line.length())
            {
                int end = TextTools.lastIndexOfNonWhitespace(line, minIndent);
                if (end >= minIndent)
                {
                    builder.append(line, minIndent, end + Character.charCount(line.codePointAt(end)));
                }
            }
        }
        return StringEscape.unescape(builder.toString());
    }
}
