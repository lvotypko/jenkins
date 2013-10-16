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


import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.security.AccessDeniedException2;
import hudson.security.HudsonPrivateSecurityRealm;
import java.io.IOException;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.junit.Assert;
import org.junit.Test;
import hudson.model.*;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContextHolder;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Lucie Votypkova
 */
public class LabelAtomTest {
    
    @Rule
    public JenkinsRule rule = new JenkinsRule();
    
    @Test
    public void getActionsTest(){
        LabelAtom label = rule.jenkins.getLabelAtom("label");
        Assert.assertFalse("List of action should not be empty", label.getActions().isEmpty());
        label = rule.jenkins.getLabelAtom("withAction");
        label.getProperties().add(new LabelAtomPropertyImpl());
        label.updateTransientActions();
        Assert.assertNotNull("List of action should contains action from property.", label.getAction(ActionImpl.class));
        
    }
    
    @Test
    public void updateTransientActions(){
        LabelAtom label = rule.jenkins.getLabelAtom("withAction");
        label.getProperties().add(new LabelAtomPropertyImpl());
        label.updateTransientActions();
        Assert.assertNotNull("List of action is not updated.", label.getAction(ActionImpl.class));
    }
    
    @Test
    public void saveAndLoad() throws IOException{
        LabelAtom label = rule.jenkins.getLabelAtom("withAction");
        label.getProperties().add(new LabelAtomPropertyImpl());
        label.save();
        Assert.assertTrue("Configuration of label was not saved with persistent all data.", label.getConfigFile().asString().contains("LabelAtomPropertyImpl"));
        label.load();
        Assert.assertNotNull("Configuration was not loaded wit all persistent data.", (label.getProperties().get(LabelAtomPropertyImpl.class)));
        
    }
    
    @Test
    public void doConfigSubmit() throws Exception{
        LabelAtom label = rule.jenkins.getLabelAtom("withAction");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        rule.jenkins.setAuthorizationStrategy(auth);
        rule.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        rule.jenkins.setSecurityRealm(realm); 
        User user = realm.createAccount("John Smith", "password");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate()); 
        try{
            label.doConfigSubmit(null, null);
            Assert.fail("User should not have permission to build project");
        }
        catch(Exception e){
            if(!(e.getClass().isAssignableFrom(AccessDeniedException2.class))){
               Assert.fail("AccessDeniedException should be thrown.");
            }
        } 
        auth.add(Jenkins.ADMINISTER, user.getId());;     
        HtmlPage page = rule.createWebClient().login(user.getId(), "password").goTo("label/withAction/configure");  
        HtmlForm form = page.getFormByName("config");
        form.getInputByName("hudson-model-labels-LabelAtomTest$LabelAtomPropertyImpl").click();
        rule.submit(form);
        Assert.assertNotNull("Label should has LabelAtomPropertyImpl property.", label.getProperties().get(LabelAtomPropertyImpl.class));
        Assert.assertNotNull("Transient actions shoudl be updated.", label.getAction(ActionImpl.class));
        
       
    }
    
    @Test
    public void get(){
        LabelAtom label = rule.jenkins.getLabelAtom("label");
        LabelAtom label2 = LabelAtom.get("label");
        Assert.assertSame("Should return for the same name the same label instance.", label, label2);
    }
    
    @Test
    public void findNearest() throws Exception{
       LabelAtom label2 = rule.jenkins.getLabelAtom("label2");
       LabelAtom label3 = rule.jenkins.getLabelAtom("label3");
       LabelAtom label4 = rule.jenkins.getLabelAtom("labelAtom");
       Assert.assertEquals("The nearest label should be label 'master' because no other label assigned any node.", rule.jenkins.getLabelAtom("master"), LabelAtom.findNearest("label"));
       Slave slave = rule.createSlave("label2 label3 labelAtom", null);
       Assert.assertEquals("The nearest label should be label 'label2'.", label2, LabelAtom.findNearest("label"));
        
    }
    
  //  @TestExtension
    public static class LabelAtomPropertyImpl extends LabelAtomProperty {
        
        @DataBoundConstructor
        public LabelAtomPropertyImpl(){
            
        }
        
        @Override
        public Collection<? extends Action> getActions(LabelAtom atom){
            if(atom.getName().equals("withAction"))
                return Collections.singleton(new ActionImpl());
            return new ArrayList<Action>();
        }
        
        @TestExtension
        public static class DescriptorImpl extends LabelAtomPropertyDescriptor {
            
            @Override
            public String getDisplayName() {
                return "Test label atom property";
            }
        }
    }
    
    @TestExtension
    public static class ActionImpl extends InvisibleAction{

        
        
    }
    
}
