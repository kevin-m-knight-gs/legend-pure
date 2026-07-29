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

package org.finos.legend.pure.m3.serialization.grammar.v1;

import org.finos.legend.pure.m4.ModelRepository;
import org.junit.Test;

public class TestStringParsing extends AbstractPrimitiveParsingTest
{
    // Characters that a Unicode-aware line break pattern matches but which JLS 3.4 does not accept as line
    // terminators: only LF, CR and CRLF end a line. All but NEXT_LINE are Character.isWhitespace.
    private static final String FORM_FEED = "\u000C";
    private static final String LINE_TABULATION = "\u000B";
    private static final String LINE_SEPARATOR = "\u2028";
    private static final String PARAGRAPH_SEPARATOR = "\u2029";
    private static final String NEXT_LINE = "\u0085";

    // Supplementary characters, each two chars wide, so a char index into a line is not a count of characters.
    private static final String AVESTAN_A = "\uD802\uDF00";      // U+10B00 AVESTAN LETTER A
    private static final String LINEAR_B_A = "\uD800\uDC00";     // U+10000 LINEAR B SYLLABLE B008 A
    private static final String LINEAR_A_AB001 = "\uD801\uDE00"; // U+10600 LINEAR A SIGN AB001

    // Space characters that Character.isWhitespace accepts, making them incidental whitespace ...
    private static final String IDEOGRAPHIC_SPACE = "\u3000";
    private static final String EN_QUAD = "\u2000";
    private static final String OGHAM_SPACE_MARK = "\u1680";

    // ... and space-like characters it rejects, making them content.
    private static final String NO_BREAK_SPACE = "\u00A0";
    private static final String NARROW_NO_BREAK_SPACE = "\u202F";
    private static final String FIGURE_SPACE = "\u2007";
    private static final String ZERO_WIDTH_SPACE = "\u200B";
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    @Test
    public void testSimpleString()
    {
        assertParsesTo("the quick brown fox jumps over the lazy dog", "'the quick brown fox jumps over the lazy dog'");
    }

    @Test
    public void testStringWithEscapedQuotes()
    {
        assertParsesTo("the quick brown 'fox' jumps over the lazy 'dog'", "'the quick brown \\'fox\\' jumps over the lazy \\'dog\\''");
    }

    @Test
    public void testStringWithDoubleQuotes()
    {
        assertParsesTo("the quick brown \"fox\" jumps over the lazy \"dog\"", "'the quick brown \"fox\" jumps over the lazy \"dog\"'");
    }

    @Test
    public void testStringWithNewline()
    {
        assertParsesTo("the quick brown fox\njumps over the lazy dog", "'the quick brown fox\\njumps over the lazy dog'");
    }

    @Test
    public void testStringWithEscapedSlash()
    {
        assertParsesTo("the quick brown fox \\ jumps over the lazy dog", "'the quick brown fox \\\\ jumps over the lazy dog'");
    }

    @Test
    public void testEscapedSlashThenQuoteThenTextThenQuote()
    {
        assertFailsToParse("'\\\\'outside quote'"); // '\\'outside quote'
    }

    @Test
    public void testStringWithUnescapedQuote()
    {
        assertFailsToParse("'''");
    }

    @Test
    public void testSimpleMultilineString()
    {
        assertParsesTo("line1\nline2", "'''\nline1\nline2'''");
    }

    @Test
    public void testMultilineStringWithTrailingNewline()
    {
        assertParsesTo("line1\nline2\n", "'''\nline1\nline2\n'''");
    }

    @Test
    public void testMultilineStringIndentationStripped()
    {
        assertParsesTo("hello\nworld\n", "'''\n    hello\n    world\n    '''");
    }

    @Test
    public void testMultilineStringProcessesEscapes()
    {
        assertParsesTo("a\tb\n", "'''\na\\tb\n'''");
        assertParsesTo("a\nb\n", "'''\na\\nb\n'''");
        assertParsesTo("a\rb\n", "'''\na\\rb\n'''");
        assertParsesTo("a\bb\n", "'''\na\\bb\n'''");
        assertParsesTo("a\fb\n", "'''\na\\fb\n'''");
        assertParsesTo("a\\b\n", "'''\na\\\\b\n'''");
        assertParsesTo("a'b\n", "'''\na\\'b\n'''");
        assertParsesTo("aAb\n", "'''\na\\101b\n'''");
        assertParsesTo("aAb\n", "'''\na\\u0041b\n'''");
    }

    @Test
    public void testMultilineStringEscapesProcessedAfterStrippingWhitespace()
    {
        // Incidental whitespace is removed before escapes are translated, so an escape that yields whitespace at the
        // end of a line survives, where the same character written literally would have been stripped.
        assertParsesTo("hello\t\nworld\n", "'''\n    hello\\t\n    world\n    '''");
        assertParsesTo("hello\n\n", "'''\n    hello\\n\n    '''");
        assertParsesTo("hello \n", "'''\n    hello\\u0020\n    '''");
    }

    @Test
    public void testMultilineStringWithSupplementaryCharacters()
    {
        assertParsesTo("a" + AVESTAN_A + "b\n", "'''\n    a" + AVESTAN_A + "b\n    '''");
        assertParsesTo(AVESTAN_A + "hello\n", "'''\n" + AVESTAN_A + "hello\n    '''");
    }

    @Test
    public void testMultilineStringSupplementaryCharacterAtEndOfLine()
    {
        // Stripping trailing whitespace must not cut the last character in half when it is two chars wide.
        assertParsesTo("hello" + AVESTAN_A + "\n", "'''\n    hello" + AVESTAN_A + "\n    '''");
        assertParsesTo("hello" + AVESTAN_A + "\n", "'''\n    hello" + AVESTAN_A + "   \n    '''");
        assertParsesTo(AVESTAN_A + "\n", "'''\n    " + AVESTAN_A + "\n    '''");
        assertParsesTo("a" + LINEAR_B_A + "\nb" + LINEAR_A_AB001 + "\n",
                "'''\n    a" + LINEAR_B_A + "\n    b" + LINEAR_A_AB001 + "\n    '''");

        // including the last line, which has no terminator after it
        assertParsesTo("hello\nworld" + AVESTAN_A, "'''\n    hello\n    world" + AVESTAN_A + "'''");
    }

    @Test
    public void testMultilineStringIndentationNeverSplitsASupplementaryCharacter()
    {
        // Indentation is only ever whitespace and no whitespace character is supplementary, so the common indent
        // always falls on a character boundary - even where a shallower line drags it to the left.
        assertParsesTo(AVESTAN_A + "x\n    y\n", "'''\n  " + AVESTAN_A + "x\n      y\n    '''");
        assertParsesTo("x\n  " + AVESTAN_A + "y\n", "'''\n    x\n      " + AVESTAN_A + "y\n    '''");
    }

    @Test
    public void testMultilineStringSupplementaryCharacterFromEscapes()
    {
        // A surrogate pair written as two escapes is assembled after the whitespace strip, so the escape text is
        // what gets measured and the pair comes out intact.
        assertParsesTo("a" + AVESTAN_A + "b\n", "'''\n    a\\uD802\\uDF00b\n    '''");
        assertParsesTo("hello" + AVESTAN_A + "\n", "'''\n    hello\\uD802\\uDF00\n    '''");

        // an unpaired surrogate survives as itself
        assertParsesTo("hello\uD802\n", "'''\n    hello\\uD802\n    '''");
    }

    @Test
    public void testMultilineStringUnicodeSpaceAsIndentation()
    {
        // Character.isWhitespace accepts these, so they behave exactly as a space or a tab does.
        assertParsesTo("hello\n", "'''\n" + IDEOGRAPHIC_SPACE + IDEOGRAPHIC_SPACE + "hello\n" + IDEOGRAPHIC_SPACE + IDEOGRAPHIC_SPACE + "'''");
        assertParsesTo("hello\n", "'''\n" + EN_QUAD + EN_QUAD + "hello\n" + EN_QUAD + EN_QUAD + "'''");
        assertParsesTo("hello\n", "'''\n" + OGHAM_SPACE_MARK + OGHAM_SPACE_MARK + "hello\n" + OGHAM_SPACE_MARK + OGHAM_SPACE_MARK + "'''");
        assertParsesTo("hello\n", "'''\n    hello" + IDEOGRAPHIC_SPACE + "\n    '''");
    }

    @Test
    public void testMultilineStringNonBreakingAndZeroWidthSpacesAreContent()
    {
        // Character.isWhitespace rejects the non-breaking and zero width spaces, so they are never stripped and
        // never count as indentation: a line led by one of them is not indented at all.
        assertParsesTo(NO_BREAK_SPACE + NO_BREAK_SPACE + "hello\n", "'''\n" + NO_BREAK_SPACE + NO_BREAK_SPACE + "hello\n    '''");
        assertParsesTo("hello" + NO_BREAK_SPACE + "\n", "'''\n    hello" + NO_BREAK_SPACE + "\n    '''");
        assertParsesTo("hello" + NARROW_NO_BREAK_SPACE + "\n", "'''\n    hello" + NARROW_NO_BREAK_SPACE + "\n    '''");
        assertParsesTo("hello" + FIGURE_SPACE + "\n", "'''\n    hello" + FIGURE_SPACE + "\n    '''");
        assertParsesTo("hello" + ZERO_WIDTH_SPACE + "\n", "'''\n    hello" + ZERO_WIDTH_SPACE + "\n    '''");
        assertParsesTo("hello" + BYTE_ORDER_MARK + "\n", "'''\n    hello" + BYTE_ORDER_MARK + "\n    '''");
    }

    @Test
    public void testMultilineEmptyString()
    {
        assertParsesTo("", "'''\n'''");
    }

    @Test
    public void testMultilineStringStripsTrailingWhitespace()
    {
        assertParsesTo("hello\nworld\n", "'''\n    hello   \n    world  \n    '''");
        assertParsesTo("hello\n", "'''\n    hello\t\n    '''");
    }

    @Test
    public void testMultilineStringWithBlankLine()
    {
        // A blank line does not contribute to the common indentation, and is emptied out whatever its width.
        assertParsesTo("hello\n\nworld\n", "'''\n    hello\n\n    world\n    '''");
        assertParsesTo("hello\n\nworld\n", "'''\n    hello\n  \n    world\n    '''");
        assertParsesTo("hello\n\nworld\n", "'''\n    hello\n      \n    world\n    '''");
    }

    @Test
    public void testMultilineStringWithLeadingBlankLine()
    {
        assertParsesTo("\nhello\n", "'''\n\n    hello\n    '''");
    }

    @Test
    public void testMultilineStringWithTrailingBlankLine()
    {
        assertParsesTo("hello\n\n", "'''\n    hello\n\n    '''");
    }

    @Test
    public void testMultilineStringOfBlankLinesOnly()
    {
        assertParsesTo("\n", "'''\n\n'''");
        assertParsesTo("\n", "'''\n    \n    '''");
        assertParsesTo("\n", "'''\n        \n    '''");
    }

    @Test
    public void testMultilineStringClosingDelimiterSetsIndentationFloor()
    {
        // The closing delimiter line counts towards the common indentation even though it is blank, so a closing
        // delimiter to the left of the content leaves that content indented.
        assertParsesTo("    hello\n", "'''\n        hello\n    '''");
        assertParsesTo("    hello\n", "'''\n    hello\n'''");

        // A closing delimiter to the right of the content does not over-strip it.
        assertParsesTo("hello\n", "'''\n    hello\n        '''");
    }

    @Test
    public void testMultilineStringClosingDelimiterOnContentLine()
    {
        // With no line terminator before the closing delimiter there is no trailing newline, and the last line is
        // an ordinary non-blank line for the purpose of computing the common indentation.
        assertParsesTo("hello\nworld", "'''\n    hello\n    world'''");
        assertParsesTo("    hello\nworld", "'''\n        hello\n    world'''");
    }

    @Test
    public void testMultilineStringIndentationCountedInChars()
    {
        // Tabs are not expanded: one tab is one char of indentation, and a tab and a space are interchangeable.
        assertParsesTo("hello\nworld\n", "'''\n\thello\n\tworld\n\t'''");
        assertParsesTo("hello\nworld\n", "'''\n\t hello\n \tworld\n    '''");
    }

    @Test
    public void testMultilineStringIndentationIsTheMinimumAcrossLines()
    {
        assertParsesTo("    a\nb\n        c\n", "'''\n        a\n    b\n            c\n    '''");
        assertParsesTo("a\nb\n", "'''\na\nb\n'''");
    }

    @Test
    public void testMultilineStringNormalizesLineTerminators()
    {
        assertParsesTo("hello\nworld\n", "'''\n    hello\r\n    world\r\n    '''");
        assertParsesTo("hello\nworld\n", "'''\n    hello\r    world\r    '''");
        assertParsesTo("a\nb\nc\n", "'''\n    a\r\n    b\r    c\n    '''");

        // CRLF is one line terminator, not two
        assertParsesTo("a\n\nb\n", "'''\n    a\r\n\r\n    b\n    '''");
    }

    @Test
    public void testMultilineStringNonTerminatorWhitespaceDoesNotBreakALine()
    {
        // Whitespace other than a line terminator stays in the content, and the text around it stays on one line -
        // so it takes no part in working out where lines begin, and cannot pull the common indentation down with it.
        assertParsesTo("hello" + FORM_FEED + "world\n", "'''\n    hello" + FORM_FEED + "world\n    '''");
        assertParsesTo("hello" + LINE_TABULATION + "world\n", "'''\n    hello" + LINE_TABULATION + "world\n    '''");
        assertParsesTo("hello" + LINE_SEPARATOR + "world\n", "'''\n    hello" + LINE_SEPARATOR + "world\n    '''");
        assertParsesTo("hello" + PARAGRAPH_SEPARATOR + "world\n", "'''\n    hello" + PARAGRAPH_SEPARATOR + "world\n    '''");
        assertParsesTo("hello" + NEXT_LINE + "world\n", "'''\n    hello" + NEXT_LINE + "world\n    '''");
    }

    @Test
    public void testMultilineStringNonTerminatorWhitespaceIsStillWhitespace()
    {
        // It is stripped from the end of a line ...
        assertParsesTo("hello\n", "'''\n    hello" + FORM_FEED + "\n    '''");
        assertParsesTo("hello\n", "'''\n    hello" + LINE_TABULATION + "\n    '''");
        assertParsesTo("hello\n", "'''\n    hello" + LINE_SEPARATOR + "\n    '''");
        assertParsesTo("hello\n", "'''\n    hello" + PARAGRAPH_SEPARATOR + "\n    '''");

        // ... counts towards the indentation of a line ...
        assertParsesTo("hello\n", "'''\n  " + FORM_FEED + " hello\n    '''");

        // ... and leaves a line blank when it is all that line holds
        assertParsesTo("hello\n\nworld\n", "'''\n    hello\n  " + FORM_FEED + "\n    world\n    '''");
    }

    @Test
    public void testMultilineStringNextLineIsOrdinaryContent()
    {
        // Alone among these, U+0085 NEXT LINE is not Character.isWhitespace, so it is never stripped, it does not
        // count as indentation, and it makes an otherwise blank line significant.
        assertParsesTo("hello" + NEXT_LINE + "\n", "'''\n    hello" + NEXT_LINE + "\n    '''");
        assertParsesTo(NEXT_LINE + " hello\n", "'''\n  " + NEXT_LINE + " hello\n    '''");
        assertParsesTo("  hello\n" + NEXT_LINE + "\n  world\n", "'''\n    hello\n  " + NEXT_LINE + "\n    world\n    '''");
    }

    @Test
    public void testMultilineStringWithQuotes()
    {
        assertParsesTo("it's a \"test\" and ''\n", "'''\n    it's a \"test\" and ''\n    '''");
        assertParsesTo("hello'\n", "'''\n    hello'\n    '''");
        assertParsesTo("hello'\n", "'''\n    hello\\'\n    '''");
    }

    @Test
    public void testMultilineStringWhitespaceAfterOpeningDelimiter()
    {
        assertParsesTo("hello\n", "'''   \n    hello\n    '''");
        assertParsesTo("hello\n", "'''\t\n    hello\n    '''");
        assertParsesTo("hello\n", "'''  \r\n    hello\n    '''");
    }

    @Test
    public void testMultilineStringRequiresNewlineAfterOpeningDelimiter()
    {
        assertFailsToParse("'''abc'''");
        assertFailsToParse("'''  abc\n'''");
    }

    @Test
    public void testStringWithOnlyEscapedQuote()
    {
        assertParsesTo("'", "'\\''");
    }

    @Test
    public void testStringWithOnlyEscapedSlash()
    {
        assertParsesTo("\\", "'\\\\'");
    }

    @Test
    public void testStringWithEscapedSlashAndEscapedQuote()
    {
        assertParsesTo("\\'", "'\\\\\\''");
    }

    @Test
    public void testStringWithEscapedSlashAndUnescapedQuote()
    {
        assertFailsToParse("'\\\\''");
    }

    @Override
    protected String getPrimitiveTypeName()
    {
        return ModelRepository.STRING_TYPE_NAME;
    }
}
