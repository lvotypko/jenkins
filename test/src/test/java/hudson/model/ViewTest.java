/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

import org.jvnet.hudson.test.TestExtension;
import com.gargoylesoftware.htmlunit.html.HtmlLabel;
import hudson.model.ViewPropertyTest.ViewPropertyImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.util.HudsonIsLoading;
import java.io.File;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import java.io.FileInputStream;
import hudson.XmlFile;
import java.util.List;
import java.util.ArrayList;
import hudson.matrix.LabelAxis;
import java.io.IOException;
import jenkins.model.Jenkins;
import hudson.matrix.MatrixProject;
import hudson.matrix.AxisList;
import org.jvnet.hudson.test.Bug;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonTestCase;
import org.w3c.dom.Text;

import static hudson.model.Messages.Hudson_ViewName;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.fail;

/**
 * @author Kohsuke Kawaguchi
 */
public class ViewTest extends HudsonTestCase {

    @Bug(7100)
    public void testXHudsonHeader() throws Exception {
        assertNotNull(new WebClient().goTo("/").getWebResponse().getResponseHeaderValue("X-Hudson"));
    }

    /**
     * Creating two views with the same name.
     */
    @Email("http://d.hatena.ne.jp/ssogabe/20090101/1230744150")
    public void testConflictingName() throws Exception {
        assertNull(jenkins.getView("foo"));

        HtmlForm form = new WebClient().goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("foo");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);
        submit(form);
        assertNotNull(jenkins.getView("foo"));

        // do it again and verify an error
        try {
            submit(form);
            fail("shouldn't be allowed to create two views of the same name.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(400, e.getStatusCode());
        }
    }

    public void testPrivateView() throws Exception {
        createFreeStyleProject("project1");
        User user = User.get("me", true); // create user

        WebClient wc = new WebClient();
        HtmlPage userPage = wc.goTo("/user/me");
        HtmlAnchor privateViewsLink = userPage.getFirstAnchorByText("My Views");
        assertNotNull("My Views link not available", privateViewsLink);

        HtmlPage privateViewsPage = (HtmlPage) privateViewsLink.click();

        Text viewLabel = (Text) privateViewsPage.getFirstByXPath("//table[@id='viewList']//td[@class='active']/text()");
        assertTrue("'All' view should be selected", viewLabel.getTextContent().contains(Hudson_ViewName()));

        View listView = new ListView("listView", jenkins);
        jenkins.addView(listView);

        HtmlPage newViewPage = wc.goTo("/user/me/my-views/newView");
        HtmlForm form = newViewPage.getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("proxy-view");
        ((HtmlRadioButtonInput) form.getInputByValue("hudson.model.ProxyView")).setChecked(true);
        HtmlPage proxyViewConfigurePage = submit(form);
        View proxyView = user.getProperty(MyViewsProperty.class).getView("proxy-view");
        assertNotNull(proxyView);
        form = proxyViewConfigurePage.getFormByName("viewConfig");
        form.getSelectByName("proxiedViewName").setSelectedAttribute("listView", true);
        submit(form);

        assertTrue(proxyView instanceof ProxyView);
        assertEquals(((ProxyView) proxyView).getProxiedViewName(), "listView");
        assertEquals(((ProxyView) proxyView).getProxiedView(), listView);
    }

    public void testDeleteView() throws Exception {
        WebClient wc = new WebClient();

        ListView v = new ListView("list", jenkins);
        jenkins.addView(v);
        HtmlPage delete = wc.getPage(v, "delete");
        submit(delete.getFormByName("delete"));
        assertNull(jenkins.getView("list"));

        User user = User.get("user", true);
        MyViewsProperty p = user.getProperty(MyViewsProperty.class);
        v = new ListView("list", p);
        p.addView(v);
        delete = wc.getPage(v, "delete");
        submit(delete.getFormByName("delete"));
        assertNull(p.getView("list"));

    }

    @Bug(9367)
    public void testPersistence() throws Exception {
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);

        ListView v = (ListView) Jenkins.XSTREAM.fromXML(Jenkins.XSTREAM.toXML(view));
        System.out.println(v.getProperties());
        assertNotNull(v.getProperties());
    }

    @Bug(9367)
    public void testAllImagesCanBeLoaded() throws Exception {
        User.get("user", true);
        WebClient webClient = new WebClient();
        webClient.setJavaScriptEnabled(false);
        assertAllImageLoadSuccessfully(webClient.goTo("asynchPeople"));
    }

    @Bug(16608)
    public void testNotAlloedName() throws Exception {
        HtmlForm form = new WebClient().goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("..");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);

        try {
            submit(form);
            fail("\"..\" should not be allowed.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(400, e.getStatusCode());
        }
    }
    
    public void testGetQueueItems() throws IOException, Exception{
        ListView view = new ListView("foo", jenkins);
        ListView view2 =new ListView("foo2", jenkins);
        jenkins.addView(view);
        jenkins.addView(view2);
        FreeStyleProject job1 = jenkins.createProject(FreeStyleProject.class, "free");
        MatrixProject job2 = jenkins.createProject(MatrixProject.class, "matrix");
        FreeStyleProject job = jenkins.createProject(FreeStyleProject.class, "not-in-view");
        FreeStyleProject job3 = jenkins.createProject(FreeStyleProject.class, "in-other-view");
        view.filterQueue=true;
        view.jobNames.add(job1.getDisplayName());
        view.jobNames.add(job2.getDisplayName());
        view2.filterQueue=true;
        view2.jobNames.add(job3.getDisplayName());
        job1.setAssignedLabel(jenkins.getLabelAtom("without-any-slave"));
        job2.setAssignedLabel(jenkins.getLabelAtom("without-any-slave"));
        job.setAssignedLabel(jenkins.getLabelAtom("without-any-slave"));
        job3.setAssignedLabel(jenkins.getLabelAtom("without-any-slave"));
        Queue.Item item = Queue.getInstance().schedule(job, 0);
        Queue.Item item1 = Queue.getInstance().schedule(job1, 0);
        Queue.Item item2 = Queue.getInstance().schedule(job2, 0);
        Queue.Item item3 = Queue.getInstance().schedule(job3, 0);
        Thread.sleep(1000);
        assertTrue("Queued items for view " + view.getDisplayName() + " should contain job " + job1.getDisplayName(),view.getQueueItems().contains(Queue.getInstance().getItem(job1)));
        assertTrue("Queued items for view " + view.getDisplayName() + " should contain job " + job2.getDisplayName(),view.getQueueItems().contains(Queue.getInstance().getItem(job2)));
        assertTrue("Queued items for view " + view2.getDisplayName() + " should contain job " + job3.getDisplayName(),view2.getQueueItems().contains(Queue.getInstance().getItem(job3)));
        assertFalse("Queued items for view " + view.getDisplayName() + " should not contain job " + job.getDisplayName(), view.getQueueItems().contains(Queue.getInstance().getItem(job)));
        assertFalse("Queued items for view " + view.getDisplayName() + " should not contain job " + job3.getDisplayName(), view.getQueueItems().contains(Queue.getInstance().getItem(job3)));
        assertFalse("Queued items for view " + view2.getDisplayName() + " should not contain job " + job.getDisplayName(), view2.getQueueItems().contains(Queue.getInstance().getItem(job)));
        assertFalse("Queued items for view " + view2.getDisplayName() + " should not contain job " + job1.getDisplayName(), view2.getQueueItems().contains(Queue.getInstance().getItem(job1)));
        assertFalse("Queued items for view " + view2.getDisplayName() + " should not contain job " + job2.getDisplayName(), view2.getQueueItems().contains(Queue.getInstance().getItem(job2)));
    }
    
    public void testGetComputers() throws IOException, Exception{
        ListView view = new ListView("foo", jenkins);
        ListView view2 =new ListView("foo2", jenkins);
        ListView view3 =new ListView("foo3", jenkins);
        jenkins.addView(view);
        jenkins.addView(view2);
        jenkins.addView(view3);
        FreeStyleProject job1 = jenkins.createProject(FreeStyleProject.class, "free");
        MatrixProject job2 = jenkins.createProject(MatrixProject.class, "matrix");
        List<String> values = new ArrayList();
        values.add("label2");
        LabelAxis axis = new LabelAxis("label",values);
        AxisList axisList = new AxisList();
        axisList.add(axis);
        job2.setAxes(axisList);
        FreeStyleProject job = jenkins.createProject(FreeStyleProject.class, "not-assigned-label");
        FreeStyleProject job3 = jenkins.createProject(FreeStyleProject.class, "in-other-view");
        job.setAssignedLabel(null);
        view.filterExecutors=true;
        view.jobNames.add(job1.getDisplayName());
        view.jobNames.add(job2.getDisplayName());
        view2.filterExecutors=true;
        view2.jobNames.add(job3.getDisplayName());
        view3.filterExecutors=true;
        view3.jobNames.add(job.getDisplayName());
        Label label1 = jenkins.getLabel("label1");
        Label label2 = jenkins.getLabel("label2");
        Label label3 = jenkins.getLabel("label3");
        job1.setAssignedLabel(jenkins.getLabel("label1||label3"));
        job3.setAssignedLabel(jenkins.getLabel("label2||lable1"));
        Slave slave1 = createOnlineSlave(label1);
        Slave slave2 = createOnlineSlave(label2);
        Slave slave3 = createOnlineSlave(label3);
        Slave slave4 = createOnlineSlave(label1);
        Slave slave5 = createOnlineSlave(jenkins.getLabel("label4"));
       // assertTrue("Filtered executors for view " + view.getDisplayName() + " should contain slave " + slave1.getDisplayName(),view.getComputers().contains(slave1.toComputer()));
        assertTrue("Filtered executors for view " + view.getDisplayName() + " should contain slave " + slave2.getDisplayName(),view.getComputers().contains(slave2.toComputer()));
       // assertTrue("Filtered executors for view " + view.getDisplayName() + " should contain slave " + slave3.getDisplayName(),view.getComputers().contains(slave3.toComputer()));
       // assertTrue("Filtered executors for view " + view2.getDisplayName() + " should contain slave " + slave4.getDisplayName(),view2.getComputers().contains(slave4.toComputer()));
       // assertTrue("Filtered executors for view " + view2.getDisplayName() + " should contain slave " + slave1.getDisplayName(),view2.getComputers().contains(slave1.toComputer()));
       // assertTrue("Filtered executors for view " + view2.getDisplayName() + " should contain slave " + slave2.getDisplayName(),view2.getComputers().contains(slave3.toComputer()));
       // assertTrue("Filtered executors for view " + view2.getDisplayName() + " should contain slave " + slave4.getDisplayName(),view2.getComputers().contains(slave4.toComputer()));
        assertTrue("Filtered executors for view " + view3.getDisplayName() + " should contain slave " + slave1.getDisplayName(),view3.getComputers().contains(slave1.toComputer()));
        assertTrue("Filtered executors for view " + view3.getDisplayName() + " should contain slave " + slave2.getDisplayName(),view3.getComputers().contains(slave2.toComputer()));
        assertTrue("Filtered executors for view " + view3.getDisplayName() + " should contain slave " + slave3.getDisplayName(),view3.getComputers().contains(slave3.toComputer()));
        assertTrue("Filtered executors for view " + view3.getDisplayName() + " should contain slave " + slave4.getDisplayName(),view3.getComputers().contains(slave4.toComputer()));
        assertTrue("Filtered executors for view " + view3.getDisplayName() + " should contain slave " + slave5.getDisplayName(),view3.getComputers().contains(slave5.toComputer()));
        assertFalse("Filtered executors for view " + view.getDisplayName() + " should not contain slave " + slave5.getDisplayName(), view.getComputers().contains(slave5.toComputer()));
        assertFalse("Filtered executors for view " + view2.getDisplayName() + " should not contain slave " + slave5.getDisplayName(), view2.getComputers().contains(slave5.toComputer()));
        assertFalse("Filtered executors for view " + view2.getDisplayName() + " should not contain slave " + slave5.getDisplayName(), view2.getComputers().contains(slave3.toComputer()));
    }
    
    public void testGetItem() throws Exception{
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        FreeStyleProject job1 = jenkins.createProject(FreeStyleProject.class, "free");
        MatrixProject job2 = jenkins.createProject(MatrixProject.class, "matrix");
        FreeStyleProject job3 = jenkins.createProject(FreeStyleProject.class, "not-included");
        view.jobNames.add(job2.getDisplayName());
        view.jobNames.add(job1.getDisplayName());
        assertTrue("View should return job " + job1.getDisplayName(), view.getItem("free").equals(job1));
        assertFalse("View should return null.", view.getItem("not-included")==null);
    }
    
    public void testDisplayName() throws Exception{
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        assertTrue("View should have name foo.", view.getDisplayName().equals("foo"));
    }
    
    public void testRename() throws Exception {
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        view.rename("renamed");
        assertTrue("View should have name foo.", view.getDisplayName().equals("renamed"));
        view.rename("renamed");
        assertFalse("View should not be renamed if required name has another view with same owner", view.getDisplayName().equals("rename"));
    }
    
    public void testGetOwner() throws Exception {
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        assertTrue("View should have owner jenkins.", view.getOwner().equals(jenkins));
        assertTrue("Owner should contains view foo.", jenkins.getView("foo").equals(view));
    }
    
    public void testGetOwnerItemGroup() throws Exception {
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        assertTrue("View should have owner jenkins.", view.getOwnerItemGroup().equals(jenkins.getItemGroup()));
    }
    
    public void testGetOwnerPrimaryView() throws Exception{
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        jenkins.setPrimaryView(view);
        assertTrue("View should have primary view " + view.getDisplayName(), view.getOwnerPrimaryView().equals(view));
    }
    
    public void testGetDescription() throws Exception{
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        view.description = "some description";
        assertTrue("View should have description 'some description'", "some description".equals(view.getDescription()));
    }
    
    public void testSave() throws Exception{
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        FreeStyleProject job = jenkins.createProject(FreeStyleProject.class, "free");
        view.jobNames.add("free");
        view.save();
        jenkins.doReload();
        //wait until all configuration are reloaded
        if(jenkins.servletContext.getAttribute("app") instanceof HudsonIsLoading){
            Thread.sleep(500);
        }
        assertTrue("View does not contains job free after load.", jenkins.getView(view.getDisplayName()).contains(jenkins.getItem(job.getName())));       
    }
    
    public void testGetProperties() throws Exception {
        View view = new ListView("foo", jenkins);
        jenkins.addView(view);
        Thread.sleep(100000);
        HtmlForm f = createWebClient().getPage(view, "configure").getFormByName("viewConfig");
        ((HtmlLabel)f.selectSingleNode(".//LABEL[text()='Test property']")).click();
        submit(f);
        assertTrue("View should contains ViewPropertyImpl property.", view.getProperties().get(PropertyImpl.class)!=null);
    }
    
    public void testIsDefault() throws IOException{
        View view = new ListView("foo", jenkins);
        jenkins.addView(view);
        assertFalse("View should not be deafult view.", view.getOwner().getPrimaryView().equals(view));
        jenkins.setPrimaryView(view);
        assertTrue("View should be deafult view.", view.getOwner().getPrimaryView().equals(view));
    }
    
    public static class PropertyImpl extends ViewProperty {
        public String name;

        @DataBoundConstructor
        public PropertyImpl(String name) {
            this.name = name;
        }

        @TestExtension
        public static class DescriptorImpl extends ViewPropertyDescriptor {
            @Override
            public String getDisplayName() {
                return "Test property";
            }
        }
    }
   
}
