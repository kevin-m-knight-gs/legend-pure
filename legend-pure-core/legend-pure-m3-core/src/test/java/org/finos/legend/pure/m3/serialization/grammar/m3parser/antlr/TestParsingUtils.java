// Copyright 2026 Goldman Sachs
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
import org.finos.legend.pure.m4.serialization.grammar.antlr.PureParserException;
import org.junit.Assert;
import org.junit.Test;

public class TestParsingUtils
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
    public void testRemoveLastCommaCharacterIfPresent()
    {
        Assert.assertEquals("", removeLastComma(""));
        Assert.assertEquals("", removeLastComma(","));
        Assert.assertEquals("a", removeLastComma("a"));
        Assert.assertEquals("a", removeLastComma("a,"));
        Assert.assertEquals("a,b", removeLastComma("a,b,"));
        Assert.assertEquals("a,", removeLastComma("a,,"));
        Assert.assertEquals("a, ", removeLastComma("a, "));

        // the given builder is modified in place and returned
        StringBuilder builder = new StringBuilder("a,");
        Assert.assertSame(builder, ParsingUtils.removeLastCommaCharacterIfPresent(builder));
        Assert.assertEquals("a", builder.toString());
    }

    @Test
    public void testIsAntlrRecognitionExceptionUsingFastParser()
    {
        PureParserException withRecognition = new PureParserException(null, "recognition", new RecognitionException(null, null, null));
        PureParserException withoutRecognition = new PureParserException(null, "no recognition", new RuntimeException());

        Assert.assertTrue(ParsingUtils.isAntlrRecognitionExceptionUsingFastParser(true, withRecognition));

        // every other combination is false
        Assert.assertFalse(ParsingUtils.isAntlrRecognitionExceptionUsingFastParser(false, withRecognition));
        Assert.assertFalse(ParsingUtils.isAntlrRecognitionExceptionUsingFastParser(true, withoutRecognition));
        Assert.assertFalse(ParsingUtils.isAntlrRecognitionExceptionUsingFastParser(true, new PureParserException(null, "no cause")));
        Assert.assertFalse(ParsingUtils.isAntlrRecognitionExceptionUsingFastParser(true, new RuntimeException("not a parser exception")));
        Assert.assertFalse(ParsingUtils.isAntlrRecognitionExceptionUsingFastParser(false, withoutRecognition));
    }

    @Test
    public void testProcessMultilineStringRejectsMissingDelimiters()
    {
        // too short to hold both delimiters, even where what is there looks like a delimiter
        assertInvalid("");
        assertInvalid("'");
        assertInvalid("''");
        assertInvalid("'''");
        assertInvalid("''''");
        assertInvalid("'''''");
        assertInvalid("hello");

        // no opening delimiter
        assertInvalid("hello'''");
        assertInvalid("''hello\n'''");
        assertInvalid("'' '\nhello\n'''");

        // no closing delimiter
        assertInvalid("'''\nhello");
        assertInvalid("'''\nhello''");
        assertInvalid("'''\nhello''' ");
    }

    @Test
    public void testProcessMultilineStringRejectsContentOnOpeningDelimiterLine()
    {
        // the opening delimiter must be followed by a line terminator, so nothing but whitespace may share its line
        assertInvalid("'''abc'''");
        assertInvalid("'''abc\n'''");
        assertInvalid("'''  abc\n'''");
        assertInvalid("'''  abc\n  def\n  '''");
        assertInvalid("''''\n'''");
        assertInvalid("''' " + NEXT_LINE + "\n'''");
        assertInvalid("''' " + NO_BREAK_SPACE + "\n'''");
        assertInvalid("''' " + AVESTAN_A + "\n'''");
    }

    @Test
    public void testProcessMultilineStringSimple()
    {
        assertProcessesTo("line1\nline2", "'''\nline1\nline2'''");
    }

    @Test
    public void testProcessMultilineStringWithTrailingNewline()
    {
        assertProcessesTo("line1\nline2\n", "'''\nline1\nline2\n'''");
    }

    @Test
    public void testProcessMultilineStringIndentationStripped()
    {
        assertProcessesTo("hello\nworld\n", "'''\n    hello\n    world\n    '''");
    }

    @Test
    public void testProcessMultilineStringEmpty()
    {
        assertProcessesTo("", "'''\n'''");

        // the lexer never produces this - it requires a line terminator - but nothing here depends on one
        assertProcessesTo("", "''''''");
        assertProcessesTo("", "'''   '''");
    }

    @Test
    public void testProcessMultilineStringProcessesEscapes()
    {
        assertProcessesTo("a\tb\n", "'''\na\\tb\n'''");
        assertProcessesTo("a\nb\n", "'''\na\\nb\n'''");
        assertProcessesTo("a\rb\n", "'''\na\\rb\n'''");
        assertProcessesTo("a\bb\n", "'''\na\\bb\n'''");
        assertProcessesTo("a\fb\n", "'''\na\\fb\n'''");
        assertProcessesTo("a\\b\n", "'''\na\\\\b\n'''");
        assertProcessesTo("a'b\n", "'''\na\\'b\n'''");
        assertProcessesTo("aAb\n", "'''\na\\101b\n'''");
        assertProcessesTo("aAb\n", "'''\na\\u0041b\n'''");
    }

    @Test
    public void testProcessMultilineStringEscapesProcessedAfterStrippingWhitespace()
    {
        // Incidental whitespace is removed before escapes are translated, so an escape that yields whitespace at the
        // end of a line survives, where the same character written literally would have been stripped.
        assertProcessesTo("hello\t\nworld\n", "'''\n    hello\\t\n    world\n    '''");
        assertProcessesTo("hello\n\n", "'''\n    hello\\n\n    '''");
        assertProcessesTo("hello \n", "'''\n    hello\\u0020\n    '''");
    }

    @Test
    public void testProcessMultilineStringWithSupplementaryCharacters()
    {
        assertProcessesTo("a" + AVESTAN_A + "b\n", "'''\n    a" + AVESTAN_A + "b\n    '''");
        assertProcessesTo(AVESTAN_A + "hello\n", "'''\n" + AVESTAN_A + "hello\n    '''");
    }

    @Test
    public void testProcessMultilineStringSupplementaryCharacterAtEndOfLine()
    {
        // Stripping trailing whitespace must not cut the last character in half when it is two chars wide.
        assertProcessesTo("hello" + AVESTAN_A + "\n", "'''\n    hello" + AVESTAN_A + "\n    '''");
        assertProcessesTo("hello" + AVESTAN_A + "\n", "'''\n    hello" + AVESTAN_A + "   \n    '''");
        assertProcessesTo(AVESTAN_A + "\n", "'''\n    " + AVESTAN_A + "\n    '''");
        assertProcessesTo("a" + LINEAR_B_A + "\nb" + LINEAR_A_AB001 + "\n",
                "'''\n    a" + LINEAR_B_A + "\n    b" + LINEAR_A_AB001 + "\n    '''");

        // including the last line, which has no terminator after it
        assertProcessesTo("hello\nworld" + AVESTAN_A, "'''\n    hello\n    world" + AVESTAN_A + "'''");
    }

    @Test
    public void testProcessMultilineStringIndentationNeverSplitsASupplementaryCharacter()
    {
        // Indentation is only ever whitespace and no whitespace character is supplementary, so the common indent
        // always falls on a character boundary - even where a shallower line drags it to the left.
        assertProcessesTo(AVESTAN_A + "x\n    y\n", "'''\n  " + AVESTAN_A + "x\n      y\n    '''");
        assertProcessesTo("x\n  " + AVESTAN_A + "y\n", "'''\n    x\n      " + AVESTAN_A + "y\n    '''");
    }

    @Test
    public void testProcessMultilineStringSupplementaryCharacterFromEscapes()
    {
        // A surrogate pair written as two escapes is assembled after the whitespace strip, so the escape text is
        // what gets measured and the pair comes out intact.
        assertProcessesTo("a" + AVESTAN_A + "b\n", "'''\n    a\\uD802\\uDF00b\n    '''");
        assertProcessesTo("hello" + AVESTAN_A + "\n", "'''\n    hello\\uD802\\uDF00\n    '''");

        // an unpaired surrogate survives as itself
        assertProcessesTo("hello\uD802\n", "'''\n    hello\\uD802\n    '''");
    }

    @Test
    public void testProcessMultilineStringUnicodeSpaceAsIndentation()
    {
        // Character.isWhitespace accepts these, so they behave exactly as a space or a tab does.
        assertProcessesTo("hello\n", "'''\n" + IDEOGRAPHIC_SPACE + IDEOGRAPHIC_SPACE + "hello\n" + IDEOGRAPHIC_SPACE + IDEOGRAPHIC_SPACE + "'''");
        assertProcessesTo("hello\n", "'''\n" + EN_QUAD + EN_QUAD + "hello\n" + EN_QUAD + EN_QUAD + "'''");
        assertProcessesTo("hello\n", "'''\n" + OGHAM_SPACE_MARK + OGHAM_SPACE_MARK + "hello\n" + OGHAM_SPACE_MARK + OGHAM_SPACE_MARK + "'''");
        assertProcessesTo("hello\n", "'''\n    hello" + IDEOGRAPHIC_SPACE + "\n    '''");
    }

    @Test
    public void testProcessMultilineStringNonBreakingAndZeroWidthSpacesAreContent()
    {
        // Character.isWhitespace rejects the non-breaking and zero width spaces, so they are never stripped and
        // never count as indentation: a line led by one of them is not indented at all.
        assertProcessesTo(NO_BREAK_SPACE + NO_BREAK_SPACE + "hello\n", "'''\n" + NO_BREAK_SPACE + NO_BREAK_SPACE + "hello\n    '''");
        assertProcessesTo("hello" + NO_BREAK_SPACE + "\n", "'''\n    hello" + NO_BREAK_SPACE + "\n    '''");
        assertProcessesTo("hello" + NARROW_NO_BREAK_SPACE + "\n", "'''\n    hello" + NARROW_NO_BREAK_SPACE + "\n    '''");
        assertProcessesTo("hello" + FIGURE_SPACE + "\n", "'''\n    hello" + FIGURE_SPACE + "\n    '''");
        assertProcessesTo("hello" + ZERO_WIDTH_SPACE + "\n", "'''\n    hello" + ZERO_WIDTH_SPACE + "\n    '''");
        assertProcessesTo("hello" + BYTE_ORDER_MARK + "\n", "'''\n    hello" + BYTE_ORDER_MARK + "\n    '''");
    }

    @Test
    public void testProcessMultilineStringStripsTrailingWhitespace()
    {
        assertProcessesTo("hello\nworld\n", "'''\n    hello   \n    world  \n    '''");
        assertProcessesTo("hello\n", "'''\n    hello\t\n    '''");
    }

    @Test
    public void testProcessMultilineStringWithBlankLine()
    {
        // A blank line does not contribute to the common indentation, and is emptied out whatever its width.
        assertProcessesTo("hello\n\nworld\n", "'''\n    hello\n\n    world\n    '''");
        assertProcessesTo("hello\n\nworld\n", "'''\n    hello\n  \n    world\n    '''");
        assertProcessesTo("hello\n\nworld\n", "'''\n    hello\n      \n    world\n    '''");
    }

    @Test
    public void testProcessMultilineStringWithLeadingBlankLine()
    {
        assertProcessesTo("\nhello\n", "'''\n\n    hello\n    '''");
    }

    @Test
    public void testProcessMultilineStringWithTrailingBlankLine()
    {
        assertProcessesTo("hello\n\n", "'''\n    hello\n\n    '''");
    }

    @Test
    public void testProcessMultilineStringOfBlankLinesOnly()
    {
        assertProcessesTo("\n", "'''\n\n'''");
        assertProcessesTo("\n", "'''\n    \n    '''");
        assertProcessesTo("\n", "'''\n        \n    '''");
    }

    @Test
    public void testProcessMultilineStringClosingDelimiterSetsIndentationFloor()
    {
        // The closing delimiter line counts towards the common indentation even though it is blank, so a closing
        // delimiter to the left of the content leaves that content indented.
        assertProcessesTo("    hello\n", "'''\n        hello\n    '''");
        assertProcessesTo("    hello\n", "'''\n    hello\n'''");

        // A closing delimiter to the right of the content does not over-strip it.
        assertProcessesTo("hello\n", "'''\n    hello\n        '''");
    }

    @Test
    public void testProcessMultilineStringClosingDelimiterOnContentLine()
    {
        // With no line terminator before the closing delimiter there is no trailing newline, and the last line is
        // an ordinary non-blank line for the purpose of computing the common indentation.
        assertProcessesTo("hello\nworld", "'''\n    hello\n    world'''");
        assertProcessesTo("    hello\nworld", "'''\n        hello\n    world'''");
    }

    @Test
    public void testProcessMultilineStringIndentationCountedInChars()
    {
        // Tabs are not expanded: one tab is one char of indentation, and a tab and a space are interchangeable.
        assertProcessesTo("hello\nworld\n", "'''\n\thello\n\tworld\n\t'''");
        assertProcessesTo("hello\nworld\n", "'''\n\t hello\n \tworld\n    '''");
    }

    @Test
    public void testProcessMultilineStringIndentationIsTheMinimumAcrossLines()
    {
        assertProcessesTo("    a\nb\n        c\n", "'''\n        a\n    b\n            c\n    '''");
        assertProcessesTo("a\nb\n", "'''\na\nb\n'''");
    }

    @Test
    public void testProcessMultilineStringNormalizesLineTerminators()
    {
        assertProcessesTo("hello\nworld\n", "'''\n    hello\r\n    world\r\n    '''");
        assertProcessesTo("hello\nworld\n", "'''\n    hello\r    world\r    '''");
        assertProcessesTo("a\nb\nc\n", "'''\n    a\r\n    b\r    c\n    '''");

        // CRLF is one line terminator, not two
        assertProcessesTo("a\n\nb\n", "'''\n    a\r\n\r\n    b\n    '''");

        // the opening delimiter line may end with any of the three, though the lexer only ever produces LF or CRLF
        assertProcessesTo("hello\n", "'''\r    hello\n    '''");
        assertProcessesTo("hello\n", "'''\r\n    hello\n    '''");
    }

    @Test
    public void testProcessMultilineStringNonTerminatorWhitespaceDoesNotBreakALine()
    {
        // Whitespace other than a line terminator stays in the content, and the text around it stays on one line -
        // so it takes no part in working out where lines begin, and cannot pull the common indentation down with it.
        assertProcessesTo("hello" + FORM_FEED + "world\n", "'''\n    hello" + FORM_FEED + "world\n    '''");
        assertProcessesTo("hello" + LINE_TABULATION + "world\n", "'''\n    hello" + LINE_TABULATION + "world\n    '''");
        assertProcessesTo("hello" + LINE_SEPARATOR + "world\n", "'''\n    hello" + LINE_SEPARATOR + "world\n    '''");
        assertProcessesTo("hello" + PARAGRAPH_SEPARATOR + "world\n", "'''\n    hello" + PARAGRAPH_SEPARATOR + "world\n    '''");
        assertProcessesTo("hello" + NEXT_LINE + "world\n", "'''\n    hello" + NEXT_LINE + "world\n    '''");
    }

    @Test
    public void testProcessMultilineStringNonTerminatorWhitespaceIsStillWhitespace()
    {
        // It is stripped from the end of a line ...
        assertProcessesTo("hello\n", "'''\n    hello" + FORM_FEED + "\n    '''");
        assertProcessesTo("hello\n", "'''\n    hello" + LINE_TABULATION + "\n    '''");
        assertProcessesTo("hello\n", "'''\n    hello" + LINE_SEPARATOR + "\n    '''");
        assertProcessesTo("hello\n", "'''\n    hello" + PARAGRAPH_SEPARATOR + "\n    '''");

        // ... counts towards the indentation of a line ...
        assertProcessesTo("hello\n", "'''\n  " + FORM_FEED + " hello\n    '''");

        // ... and leaves a line blank when it is all that line holds
        assertProcessesTo("hello\n\nworld\n", "'''\n    hello\n  " + FORM_FEED + "\n    world\n    '''");
    }

    @Test
    public void testProcessMultilineStringNextLineIsOrdinaryContent()
    {
        // Alone among these, U+0085 NEXT LINE is not Character.isWhitespace, so it is never stripped, it does not
        // count as indentation, and it makes an otherwise blank line significant.
        assertProcessesTo("hello" + NEXT_LINE + "\n", "'''\n    hello" + NEXT_LINE + "\n    '''");
        assertProcessesTo(NEXT_LINE + " hello\n", "'''\n  " + NEXT_LINE + " hello\n    '''");
        assertProcessesTo("  hello\n" + NEXT_LINE + "\n  world\n", "'''\n    hello\n  " + NEXT_LINE + "\n    world\n    '''");
    }

    @Test
    public void testProcessMultilineStringWithQuotes()
    {
        assertProcessesTo("it's a \"test\" and ''\n", "'''\n    it's a \"test\" and ''\n    '''");
        assertProcessesTo("hello'\n", "'''\n    hello'\n    '''");
        assertProcessesTo("hello'\n", "'''\n    hello\\'\n    '''");
    }

    @Test
    public void testProcessMultilineStringWhitespaceAfterOpeningDelimiter()
    {
        assertProcessesTo("hello\n", "'''   \n    hello\n    '''");
        assertProcessesTo("hello\n", "'''\t\n    hello\n    '''");
        assertProcessesTo("hello\n", "'''  \r\n    hello\n    '''");

        // any whitespace will do here, though the lexer only admits spaces and tabs
        assertProcessesTo("hello\n", "'''" + IDEOGRAPHIC_SPACE + "\n    hello\n    '''");
    }

    private static String removeLastComma(String string)
    {
        return ParsingUtils.removeLastCommaCharacterIfPresent(new StringBuilder(string)).toString();
    }

    private static void assertProcessesTo(String expected, String rawTokenText)
    {
        Assert.assertEquals(expected, ParsingUtils.processMultilineString(rawTokenText));
    }

    private static void assertInvalid(String rawTokenText)
    {
        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> ParsingUtils.processMultilineString(rawTokenText));
        Assert.assertEquals("Invalid multi-line string: " + rawTokenText, e.getMessage());
    }
}
