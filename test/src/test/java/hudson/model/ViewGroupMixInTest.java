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
package hudson.model;

import hudson.model.Descriptor.FormException;
import java.io.IOException;
import java.util.List;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 *
 * @author lucinka
 */
public class ViewGroupMixInTest extends HudsonTestCase{
    
    
    private ViewGroupMixIn createViewGroupMixInInstance(){
            ViewGroupMixIn viewGroup = new ViewGroupMixIn(jenkins){
            @Override
            protected List<View> views() {
               return (List<View>) jenkins.getViews();
            }

            @Override
            protected String primaryView() {
                return jenkins.getPrimaryView().getDisplayName();
            }

            @Override
            protected void primaryView(String newName) {
                jenkins.setPrimaryView(jenkins.getView(newName));
            }
           
       };
        return viewGroup;
    }
    
    public void testAddView() throws IOException{
       ViewGroupMixIn viewGroup = createViewGroupMixInInstance();
       View view = new ListView("foo");
       jenkins.addView(view);
       viewGroup.addView(view);
       assertTrue("The owener of view should be Jenkins.", jenkins.equals(view.getOwner()));
       assertTrue("View group should contain view foo.", viewGroup.views().contains(view));
    }
    
    public void testCanDelete() throws IOException{
        View view = new ListView("foo", jenkins);
        jenkins.addView(view);
        ViewGroupMixIn viewGroup = createViewGroupMixInInstance();
        assertFalse("View All is primary view so can not be deleted.", viewGroup.canDelete(jenkins.getView("All")));
        assertTrue("View foo can is not primory view so can be deleted.", viewGroup.canDelete(view));
    }
    
    public void testDeleteView() throws IOException{
        View view = new ListView("foo", jenkins);
        jenkins.addView(view);
        ViewGroupMixIn viewGroup = createViewGroupMixInInstance();
        boolean exceptionThrown = false;
        try{
            viewGroup.deleteView(jenkins.getView("All"));
        }
        catch(IllegalStateException e){
            exceptionThrown = true;
        }
        assertTrue("There should be thrown IllegalStateException if it is trying to delete primary view ", exceptionThrown);
        viewGroup.deleteView(view);
        assertTrue("View foo should be delted.", viewGroup.views().contains(view));    
    }
    
    public void testGetView() throws IOException{
        View view = new ListView("foo", jenkins);
        jenkins.addView(view);
        ViewGroupMixIn viewGroup = createViewGroupMixInInstance();
        assertTrue("It returns " + viewGroup.getView(view.getDisplayName()) + " instead of view " + view, view.equals(viewGroup.getView(view.getDisplayName())));
    }
    
    public void testGetViews() throws IOException{
        ViewGroupMixIn viewGroup = createViewGroupMixInInstance();
        View view = new ListView("foo", jenkins);
        viewGroup.views().add(view);
        jenkins.addView(view);
        assertTrue("It should contains view foo.", viewGroup.getViews().contains(view));
    }
    
    public void testGetPrimaryView() throws IOException{
        ViewGroupMixIn viewGroup = createViewGroupMixInInstance();
        View view = new ListView("foo", jenkins);
        viewGroup.views().add(view);
        jenkins.addView(view);
        viewGroup.primaryView(view.getDisplayName());
        assertTrue("View foo should be the primary view.", view.equals(viewGroup.getPrimaryView()));
    }
    
    public void testOnViewRenamed() throws IOException, Failure, FormException {
        ViewGroupMixIn viewGroup = createViewGroupMixInInstance();
        View viewPrimary = new ListView("primary", jenkins);
        jenkins.addView(viewPrimary);
        viewGroup.views().add(viewPrimary);
        viewGroup.primaryView(viewPrimary.getDisplayName());
        viewPrimary.rename("primary-renamed");       
        assertTrue("Method getPrimaryView() should return primary-renamed instead of " + viewGroup.getPrimaryView().getDisplayName(), "primary-renamed".equals(viewGroup.getPrimaryView().getDisplayName()));        
        View view = new ListView("foo", jenkins);
        jenkins.addView(view);
        view.rename("renamed");
        assertTrue("Method getView(String name) should return renamed view.", view.equals(viewGroup.getView("renamed")));
    }
}
