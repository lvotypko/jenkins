/*
 * The MIT License
 *
 * Copyright 2013 lucinka.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model.labels;

import org.junit.Assert;
import org.junit.Test;
/**
 *
 * @author Lucie Votypkova
 */
public class LabelAtomTest {
    
    @Test
    public void needsEscapeTest(){
        //why is contloed \n and\t twice?
        Assert.assertTrue("Label which contains space should be escaped.", LabelAtom.needsEscape("label1 "));
        Assert.assertTrue("Label which contains bracket should be escaped.", LabelAtom.needsEscape("label1(label2)"));
        Assert.assertTrue("Label which contains \\t should be escaped.", LabelAtom.needsEscape("label1\t"));
        Assert.assertTrue("Label which contains \\n should be escaped.", LabelAtom.needsEscape("label1\n"));
        Assert.assertTrue("Label which contains & should be escaped.", LabelAtom.needsEscape("label1&&label2"));
        Assert.assertTrue("Label which contains | should be escaped.", LabelAtom.needsEscape("label1||label2"));
        Assert.assertTrue("Label which contains ? should be escaped.", LabelAtom.needsEscape("label?"));
        Assert.assertTrue("Label which contains * should be escaped.", LabelAtom.needsEscape("label*"));
        Assert.assertTrue("Label which contains \\ should be escaped.", LabelAtom.needsEscape("label\\"));
        Assert.assertTrue("Label which contains % should be escaped.", LabelAtom.needsEscape("label%"));
        Assert.assertTrue("Label which contains # should be escaped.", LabelAtom.needsEscape("label#"));
        Assert.assertTrue("Label which contains ! should be escaped.", LabelAtom.needsEscape("label!"));
        Assert.assertTrue("Label which contains @ should be escaped.", LabelAtom.needsEscape("label@"));
        Assert.assertTrue("Label which contains $ should be escaped.", LabelAtom.needsEscape("label$"));
        Assert.assertTrue("Label which contains ^ should be escaped.", LabelAtom.needsEscape("label^"));
        Assert.assertTrue("Label which contains < should be escaped.", LabelAtom.needsEscape("label<="));
        Assert.assertTrue("Label which contains > should be escaped.", LabelAtom.needsEscape("label=>"));
        Assert.assertTrue("Label which contains [ should be escaped.", LabelAtom.needsEscape("label["));
        Assert.assertTrue("Label which contains ] should be escaped.", LabelAtom.needsEscape("label]"));
        Assert.assertTrue("Label which contains : should be escaped.", LabelAtom.needsEscape("label:"));
        Assert.assertTrue("Label which contains ; should be escaped.", LabelAtom.needsEscape("label;"));
    }
    
    @Test
    public void escapeTest(){       
        Assert.assertEquals("Label 'label1&&label2' should be escaped.","\"label1&&label2\"", LabelAtom.escape("label1&&label2"));
        Assert.assertEquals("Label 'label1\\nlabel2' should be escaped.","\"label1\\nlabel2\"", LabelAtom.escape("label1\nlabel2"));
        Assert.assertEquals("Label 'label1\\tlabel2' should be escaped.","\"label1\\tlabel2\"", LabelAtom.escape("label1\tlabel2"));
        Assert.assertEquals("Label 'label1\\blabel2' should be escaped.","\"label1\\blabel2\"", LabelAtom.escape("label1\blabel2"));
        Assert.assertEquals("Label 'label1 label2' should be escaped.","\"label1 label2\"", LabelAtom.escape("label1 label2"));
        Assert.assertEquals("Label '`1234567890-=_+qwertyuiop{}asdfghjkl':\"zxcvbnm,.~' should not be escaped.","`1234567890-=_+qwertyuiop{}asdfghjkl'\"zxcvbnm,.~", LabelAtom.escape("`1234567890-=_+qwertyuiop{}asdfghjkl'\"zxcvbnm,.~"));
    }

}
