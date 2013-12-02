/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jorg Heymans
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

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.listeners.RunListener;
import hudson.tasks.ArtifactArchiver;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import hudson.tasks.Shell;
import hudson.util.CompressedFile;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Set;
import java.util.TreeSet;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.StandardArtifactManager;
import org.apache.commons.io.IOUtils;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * @author Kohsuke Kawaguchi
 */
public class RunTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Bug(17935)
    @Test
    public void getDynamicInvisibleTransientAction() throws Exception {
        TransientBuildActionFactory.all().add(0, new TransientBuildActionFactory() {

            @Override
            public Collection<? extends Action> createFor(Run target) {
                return Collections.singleton(new Action() {

                    @Override
                    public String getDisplayName() {
                        return "Test";
                    }

                    @Override
                    public String getIconFileName() {
                        return null;
                    }

                    @Override
                    public String getUrlName() {
                        return null;
                    }
                });
            }
        });
        j.assertBuildStatusSuccess(j.createFreeStyleProject("stuff").scheduleBuild2(0));
        j.createWebClient().assertFails("job/stuff/1/nonexistent", HttpURLConnection.HTTP_NOT_FOUND);
    }

    @Test
    public void testReload() throws IOException {
        FreeStyleProject project = j.createFreeStyleProject();
        FreeStyleBuild build = new FreeStyleBuild(project);
        build.description = "description";
        build.save();
        build.reload();
        Assert.assertEquals("Description of build was not reloaded.", build.description, "description");
        Assert.assertEquals("State should be FAILURE if no result is set.", Result.FAILURE, build.result);
        build.result = Result.UNSTABLE;
        build.save();
        build.reload();
        Assert.assertEquals("Result of build was not reloaded.", Result.UNSTABLE, build.result);
    }

    @Test
    public void testGetTransientActions() throws IOException {
        FreeStyleProject project = j.createFreeStyleProject();
        FreeStyleBuild build = new FreeStyleBuild(project);
        TransientActionFactory factory = TransientBuildActionFactory.all().get(TransientActionFactory.class);
        Assert.assertTrue("Transient actions should contains instance of TransientAction class.", build.getTransientActions().contains(factory.action));
        TransientAction action = new TransientAction();
        try {
            build.getTransientActions().add(action);
        } catch (Exception e) {
         
        }
        Assert.assertFalse("Transient actions should be provided in read only form so action should not be added.", build.getTransientActions().contains(action));
    }

    @Test
    public void testParseTimestampFromBuildDir() throws IOException {
        FreeStyleProject project = j.createFreeStyleProject();
        String buildDirName = project.getBuildDir().getAbsolutePath();
        project.getBuildDir().mkdir();
        File file = new File(buildDirName, "2013-11-18_10-35-49");
        file.mkdir();
        Calendar calendar = new GregorianCalendar();
        calendar.set(Calendar.YEAR, 2013);
        calendar.set(Calendar.MONTH, 10);
        calendar.set(Calendar.DAY_OF_MONTH, 18);
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        calendar.set(Calendar.MINUTE, 35);
        calendar.set(Calendar.SECOND, 49);
        Assert.assertEquals("Timestamp from build directory was parsed wrong.", calendar.getTimeInMillis(), Run.parseTimestampFromBuildDir(file), 1000);
    }

    @Test
    public void testGetBadgeActions() throws IOException {
        FreeStyleProject project = j.createFreeStyleProject();
        FreeStyleBuild build = new FreeStyleBuild(project);
        BadgeAction action = new BadgeAction();
        build.addAction(action);
        Assert.assertTrue("Build badge actions should contains which was added.", build.getBadgeActions().contains(action));
    }

    @Test
    public void testGetExecutor() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        Slave slave = j.createOnlineSlave();
        project.setAssignedLabel(slave.getSelfLabel());
        project.getBuildersList().add(new Shell("sleep 100"));
        startBuild(project, 1);
        Assert.assertEquals("Method returns wrong executor.", slave.toComputer().getExecutors().get(0), project.getLastBuild().getExecutor());
        while(project.getBuildByNumber(1).isBuilding()){
            Thread.sleep(1000);
        }
        startBuild(project, 2);
        //executor should be null because it grabs other job
        Assert.assertNull("Executor of finished build should when grabs a new job.", project.getBuildByNumber(1).getExecutor());
    }

    @Test
    public void testGetIconColor() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new Shell("sleep 5"));
        j.jenkins.getQueue().schedule(project);
        //wait until build starts
        while (project.getLastBuild() == null) {
            Thread.sleep(100);
        }
        Assert.assertEquals("Build should have color 'not built' because there is no previous finished build", BallColor.NOTBUILT_ANIME, project.getLastBuild().getIconColor());
        // wait until build is finished
        while (project.getLastBuild().isBuilding()) {
            Thread.sleep(1000);
        }
        Assert.assertEquals("Build should have color 'successfull' if the result is success.", BallColor.BLUE, project.getLastBuild().getIconColor());
        j.jenkins.getQueue().schedule(project);
        //wait until build starts
        while (project.getBuildByNumber(2) == null) {
            Thread.sleep(100);
        }
        Assert.assertEquals("Build should have color 'successfull' because previous build was successfull.", BallColor.BLUE_ANIME, project.getLastBuild().getIconColor());

    }

    @Test
    public void testGetPreviousCompletedBuild() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        project.setConcurrentBuild(true);
        project.getBuildersList().add(new Shell("sleep 100"));
        //wait until build starts
        startBuild(project, 2);
        //wait until build starts
        startBuild(project, 3);
        Assert.assertEquals("Previous completed build for build 3 should be the build 1.", project.getBuildByNumber(1), project.getLastBuild().getPreviousCompletedBuild());
        Assert.assertNull("There should not be previous completed build for build 1.", project.getBuildByNumber(1).getPreviousCompletedBuild());
    }

    @Test
    public void testGetPreviousBuildInProgress() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        project.setConcurrentBuild(true);
        project.getBuildersList().add(new Shell("sleep 100"));
        startBuild(project, 2);
        startBuild(project, 3);
        Assert.assertEquals("Previous build in progress of build 3 should be the build 2.", project.getBuildByNumber(2), project.getLastBuild().getPreviousBuildInProgress());
        //Previous build does not exist
        Assert.assertNull("There should not be previous build for build 1.", project.getBuildByNumber(1).getPreviousBuildInProgress());
        //Previous build in progress does not exist
        Assert.assertNull("There should not be previous build in progress for build 2.", project.getBuildByNumber(2).getPreviousBuildInProgress());
    }

    @Test
    public void testGetPreviousBuiltBuild() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        project.setConcurrentBuild(true);
        project.getBuildersList().add(new Shell("sleep 100"));
        startBuild(project, 2);
        AbstractBuild buildInProgress = startBuild(project, 3);
        //previous build is in progress
        Assert.assertEquals("Previous built build of build 3 should be the build 1.", project.getBuildByNumber(1), buildInProgress.getPreviousBuiltBuild());
        //previous build does not exist
        Assert.assertNull("There should not be previous built for build 1.", project.getBuildByNumber(1).getPreviousBuiltBuild());
        project.getBuildByNumber(2).getExecutor().interrupt();
        Assert.assertEquals("Previous built build of build 3 should be the build 1, because build 2 was aborded.", project.getBuildByNumber(1), buildInProgress.getPreviousBuiltBuild());
    }
    
    private AbstractBuild startBuild(AbstractProject project, int buildNumber) throws InterruptedException{
        j.jenkins.getQueue().schedule(project);
         while (project.getBuildByNumber(buildNumber)== null) {
            Thread.sleep(100);
        }
         return project.getBuildByNumber(buildNumber);
    }
    
    private AbstractBuild startBuildAndWaitUntilIsFinished(AbstractProject project, int buildNumber) throws InterruptedException{
        j.jenkins.getQueue().schedule(project);
         while (project.getBuildByNumber(buildNumber)== null || project.isBuilding()) {
            Thread.sleep(100);
        }
        return project.getBuildByNumber(buildNumber);
    }

    @Test
    public void testGetPreviousNotFailedBuild() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        startBuildAndWaitUntilIsFinished(project, 1);
        AbstractBuild failedBuild = project.getBuildByNumber(1);
        failedBuild.result = Result.FAILURE;
        failedBuild.save();
        j.buildAndAssertSuccess(project);
        AbstractBuild successfulBuild = project.getBuildByNumber(2);
        //there is only failed builds
        Assert.assertNull("Previous not failed build of build 2 should not exist, because previous build 1 failed.", successfulBuild.getPreviousNotFailedBuild());
        project.setConcurrentBuild(true);
        project.getBuildersList().add(new Shell("sleep 100"));
        startBuild(project, 3);
        AbstractBuild unstableBuildInProgress = project.getBuildByNumber(3);
        startBuild(project, 4);
        AbstractBuild buildInProgress = project.getBuildByNumber(4);
        //previous build in progress
        Assert.assertEquals("Previous not failed build of build 4 should be the build 3 because it has result 'not built'.", project.getBuildByNumber(3), buildInProgress.getPreviousNotFailedBuild());
        Assert.assertNull("There should not be previous build for build 1.", project.getBuildByNumber(2).getPreviousNotFailedBuild());
        project.getBuildByNumber(3).getExecutor().interrupt();
        //previous build aborded
        Assert.assertEquals("Previous not failed build of build 4 should be the build 3 which was aborded.", project.getBuildByNumber(3), buildInProgress.getPreviousNotFailedBuild());
        unstableBuildInProgress.result = Result.UNSTABLE;
        unstableBuildInProgress.save();
        //previous build unstable
        Assert.assertEquals("Previous not failed build of build 4 should be the build 3 which was unstable.", project.getBuildByNumber(3), buildInProgress.getPreviousNotFailedBuild());

    }

    @Test
    public void testGetPreviousFailedBuild() throws Exception{
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        AbstractBuild sucessfulBuild = project.getLastBuild();
        j.buildAndAssertSuccess(project);
        AbstractBuild failureBuild = project.getBuildByNumber(2);
        failureBuild.result = Result.FAILURE; 
        failureBuild.save();
        Assert.assertNull("Build 2 should not have previous failed build because build 1 is successful.",sucessfulBuild.getPreviousFailedBuild());
        project.setConcurrentBuild(true);
        project.getBuildersList().add(new Shell("sleep 100"));
        AbstractBuild unstableBuildInprogress = startBuild(project, 3);
        AbstractBuild buildInprogress = startBuild(project, 4);
        //previous build in progress (previous result is null)
        Assert.assertEquals("Previous failed build of build 4 should be the build 2 because the build 3 is in progress.", project.getBuildByNumber(2), buildInprogress.getPreviousFailedBuild());
        project.getBuildByNumber(3).getExecutor().interrupt();
        //previous build abborded
        Assert.assertEquals("Previous failed build of build 4 should be the build 2 because the build 3 is aborded.", project.getBuildByNumber(2), buildInprogress.getPreviousFailedBuild());
        unstableBuildInprogress.result = Result.UNSTABLE;
        unstableBuildInprogress.save();
        //previous build unstable
        Assert.assertEquals("Previous not failed build of build 4 should be the build 2 because the build 3 was unstable.", project.getBuildByNumber(2), buildInprogress.getPreviousFailedBuild());

    }

    @Test
    public void testGetPreviousSuccessfulBuild() throws Exception{
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        AbstractBuild failureBuild = project.getLastBuild();
        failureBuild.result = Result.FAILURE;
        failureBuild.save();
        j.buildAndAssertSuccess(project);
        //previous successful build does not exist
        Assert.assertNull("Build 2 should not have previous successfull build because build 1 failed.",project.getBuildByNumber(2).getPreviousSuccessfulBuild());
        project.setConcurrentBuild(true);
        project.getBuildersList().add(new Shell("sleep 100"));
        startBuild(project, 3);
        startBuild(project, 4);
        //previous build is in progress
        Assert.assertEquals("Previous failed build of build 4 should be the build 2 because the build 3 is in progress.", project.getBuildByNumber(2), project.getLastBuild().getPreviousSuccessfulBuild());
        project.getBuildByNumber(3).getExecutor().interrupt();
        //previous build is aborded
        Assert.assertEquals("Previous failed build of build 4 should be the build 2 because the build 3 is in aborded.", project.getBuildByNumber(2), project.getLastBuild().getPreviousSuccessfulBuild());
        AbstractBuild unstableBuild = project.getBuildByNumber(3);
        unstableBuild.result = Result.UNSTABLE;
        unstableBuild.save();
        //previous build is unstable
        Assert.assertEquals("Previous not failed build of build 4 should be the build 2 because the build 3 was unstable.", project.getBuildByNumber(2), project.getLastBuild().getPreviousSuccessfulBuild());
    }

    @Test
    public void testGetPreviousBuildsOverThreshold() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        AbstractBuild failureBuild = project.getBuildByNumber(1);
        AbstractBuild successfulBuild = project.getBuildByNumber(2);
        AbstractBuild unstableBuild = project.getBuildByNumber(3);
        AbstractBuild abortedBuild = project.getBuildByNumber(4);
        AbstractBuild notBuiltBuild = project.getBuildByNumber(5);
        failureBuild.result = Result.FAILURE;
        failureBuild.save();
        successfulBuild.result = Result.SUCCESS;
        successfulBuild.save();
        unstableBuild.result = Result.UNSTABLE;
        unstableBuild.save();
        abortedBuild.result= Result.ABORTED;
        abortedBuild.save();
        notBuiltBuild.result = Result.NOT_BUILT;
        notBuiltBuild.save();
        project.setConcurrentBuild(true);
        project.getBuildersList().add(new Shell("sleep 100"));
        startBuild(project, 6);
        startBuild(project, 7);
        List<FreeStyleBuild> successfulBuilds = project.getLastBuild().getPreviousBuildsOverThreshold(5, Result.SUCCESS);
        List<FreeStyleBuild> failureBuilds = project.getLastBuild().getPreviousBuildsOverThreshold(5, Result.FAILURE);
        List<FreeStyleBuild> unstableBuilds = project.getLastBuild().getPreviousBuildsOverThreshold(5, Result.UNSTABLE);
        List<FreeStyleBuild> abortedBuilds = project.getLastBuild().getPreviousBuildsOverThreshold(5, Result.ABORTED);
        List<FreeStyleBuild> notBuiltBuilds = project.getLastBuild().getPreviousBuildsOverThreshold(5, Result.NOT_BUILT);
        Assert.assertTrue("Builds over threshold failure should contains failure builds.", failureBuilds.contains(project.getBuildByNumber(1)));
        Assert.assertTrue("Builds over threshold failure should contains sucessful builds.", failureBuilds.contains(project.getBuildByNumber(2)));
        Assert.assertTrue("Builds over threshold failure should contains unstable builds.", failureBuilds.contains(project.getBuildByNumber(3)));
        Assert.assertFalse("Builds over threshold failure should not contains not built builds.", failureBuilds.contains(project.getBuildByNumber(5)));
        Assert.assertFalse("Builds over threshold failure should not contains aborted builds.", failureBuilds.contains(project.getBuildByNumber(4)));
        Assert.assertTrue("Builds over threshold unstable build should contains successful builds.", unstableBuilds.contains(project.getBuildByNumber(2)));
        Assert.assertTrue("Builds over threshold unstable build should contains unstable builds.", unstableBuilds.contains(project.getBuildByNumber(3)));
        Assert.assertFalse("Builds over threshold unstable should not contains failure builds.", unstableBuilds.contains(project.getBuildByNumber(1)));
        Assert.assertFalse("Builds over threshold unstable should not contains not built builds.", unstableBuilds.contains(project.getBuildByNumber(5)));
        Assert.assertFalse("Builds over threshold unstable should not contains aborted builds.", unstableBuilds.contains(project.getBuildByNumber(4)));
        Assert.assertTrue("Builds over threshold successful should contains successful builds.", successfulBuilds.contains(project.getBuildByNumber(2)));
        Assert.assertFalse("Builds over threshold successful should not contains unstable builds.", successfulBuilds.contains(project.getBuildByNumber(3)));
        Assert.assertFalse("Builds over threshold successful should not contains failure builds.", successfulBuilds.contains(project.getBuildByNumber(1)));
        Assert.assertFalse("Builds over threshold successful should not contains not built builds.", successfulBuilds.contains(project.getBuildByNumber(5)));
        Assert.assertFalse("Builds over threshold successful should not contains aborted builds.", successfulBuilds.contains(project.getBuildByNumber(4)));
        Assert.assertTrue("Builds over threshold not built build should contains successful builds.", notBuiltBuilds.contains(project.getBuildByNumber(2)));
        Assert.assertTrue("Builds over threshold not built build should contains unstable builds.", notBuiltBuilds.contains(project.getBuildByNumber(3)));
        Assert.assertTrue("Builds over threshold not built build should contains failure builds.", notBuiltBuilds.contains(project.getBuildByNumber(1)));
        Assert.assertTrue("Builds over threshold not built build should contains not built builds.", notBuiltBuilds.contains(project.getBuildByNumber(5)));
        Assert.assertFalse("Builds over threshold not built should not contains aborted builds.", notBuiltBuilds.contains(project.getBuildByNumber(4)));
        Assert.assertTrue("Builds over threshold aborted build should contains not successful builds.", abortedBuilds.contains(project.getBuildByNumber(2)));
        Assert.assertTrue("Builds over threshold aborted build should contains not unstable builds.", abortedBuilds.contains(project.getBuildByNumber(3)));
        Assert.assertTrue("Builds over threshold aborted build should contains not failure builds.", abortedBuilds.contains(project.getBuildByNumber(1)));
        Assert.assertTrue("Builds over threshold aborted build should contains not not built builds.", abortedBuilds.contains(project.getBuildByNumber(5)));
        Assert.assertTrue("Builds over threshold aborted build should contains not aborted builds.", abortedBuilds.contains(project.getBuildByNumber(4)));
     
        
    }

    @Test
    public void testPickArtifactManager() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        Run run = project.getLastBuild();
        run.getArtifactManager();
        //never return null
        Assert.assertNotNull("Artifact manager should not be null.", run.pickArtifactManager());
        //if there is no ArtifactManager created by factory
        ArtifactManager manager = run.pickArtifactManager();
        Assert.assertTrue("Artifact manager should be instance of StandardArtifactManager class.", manager instanceof StandardArtifactManager);
        ArtifactManagerConfiguration.get().getArtifactManagerFactories().add(new ArtifactManagerFactoryImpl());
        //if there is ArtifactManager created by factory
        manager = run.pickArtifactManager();
        Assert.assertTrue("Artifact manager should be instance of ArtifactManagerImpl class.", manager instanceof ArtifactManagerImpl);
        Assert.assertEquals("Build should return assigned artifact manager.", manager, run.pickArtifactManager());
    }
    
    private Set<String> getArtifactPaths(List<Run.Artifact> artifacts ){
        Set<String> paths = new TreeSet<String>();
        for(Run.Artifact artifact : artifacts){
            paths.add(artifact.relativePath);
        }
        return paths;
    }

    @Test
    @LocalData
    public void testGetArtifactsUpTo() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.setCustomWorkspace(j.jenkins.getRootDir().getAbsolutePath() + "/workspace");
        project.getPublishersList().add(new ArtifactArchiver("**/artifacts/**/*", "", false, false));
        j.buildAndAssertSuccess(project);
        Run run = project.getLastBuild();
        Assert.assertEquals("If the max number of artifacts is less than number of all artifacts it should return all artifacts.",5, run.getArtifactsUpTo(6).size());
        Assert.assertEquals("Returned artifacts should have the same path as artifacts of build.", getArtifactPaths(run.getArtifacts()), (getArtifactPaths(run.getArtifactsUpTo(6))));
        Assert.assertEquals("If the number of atrifacts is 4 than the size of returned list should be 4.", 4, run.getArtifactsUpTo(4).size());
        Assert.assertTrue("Returned artifacts should have the same paths as 4 artifacts of build.", getArtifactPaths(run.getArtifacts()).containsAll(getArtifactPaths(run.getArtifactsUpTo(2))));
    }

    @Test
    public void testGetLogFile() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        Run run = project.getLastBuild();
        File file = new File (run.getRootDir(), "log");
        if(!file.exists())
            file.createNewFile();
        //log file exists
        Assert.assertEquals("If log file exists it should return log file.", file, run.getLogFile());
        if(file.exists())
            file.delete();
        file = new File (run.getRootDir(), "log.gz");
        
        //log file does not exist and log.gz exists
        if(!file.exists())
            file.createNewFile();
        Assert.assertEquals("If log file does not exist and log.gz exists it should return log.gz file.", file, run.getLogFile());
        
        //log file and log.gz file do not exist
        
        file.delete();
        Assert.assertEquals("If log file ad log.gz do not exist and log.gz exists return log file even if does not exist.", new File(run.getRootDir(), "log"), run.getLogFile());
        
    }
    
    public String readStream(InputStream stream) throws IOException{
        return IOUtils.toString(new InputStreamReader(stream));
    }

    @Test
    public void testGetLogInputStream() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        Run run = project.getLastBuild();
        File file = new File (run.getRootDir(), "log");
        String logText = "This is a build log";
        PrintStream stream = new PrintStream(file);
        stream.print(logText);
        //non-compressed log
        InputStream input = run.getLogInputStream();
        Assert.assertEquals("Input stream should read the same text which was written into log.", logText, readStream(run.getLogInputStream()));
        
        //compressed log
        CompressedFile compressedFile = new CompressedFile(file);
        compressedFile.compress();
        Assert.assertEquals("Input stream should read the same text which was written into log before comression.", logText, readStream(run.getLogInputStream()));
    }


    @Test
    public void testDelete() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        Run run = project.getLastBuild();
        run.delete();
        Assert.assertTrue("Deletion of build should fire event on RunListener.", RunListener.all().get(DeleteRunListener.class).isDeleted());
        Assert.assertFalse("Build should be deleted from disk.", run.getRootDir().exists());
        Assert.assertNull("Build should be deleted from Project.", run.getParent().getBuildByNumber(run.getNumber()));
    }


    @TestExtension("testGetTransientActions")
    public static class TransientAction extends InvisibleAction {
    }

    @TestExtension("testGetTransientActions")
    public static class TransientActionFactory extends TransientBuildActionFactory {

        protected TransientAction action;

        @Override
        public Collection<Action> createFor(Run run) {
            List<Action> actions = new ArrayList<Action>();
            action = new TransientAction();
            actions.add(action);
            return actions;
        }
    }

    @TestExtension("testGetBadgeActions")
    public static class BadgeAction implements BuildBadgeAction {

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return "Badge action";
        }

        @Override
        public String getUrlName() {
            return null;
        }
    }
    
    public static class ArtifactManagerImpl extends ArtifactManager{

        @Override
        public void onLoad(Run<?, ?> build) {
            
        }

        @Override
        public void archive(FilePath workspace, Launcher launcher, BuildListener listener, Map<String, String> artifacts) throws IOException, InterruptedException {
            
        }

        @Override
        public boolean delete() throws IOException, InterruptedException {
            return true;
        }

        @Override
        public VirtualFile root() {
            return null;
        }
        
    }
    
    public static class ArtifactManagerFactoryImpl extends ArtifactManagerFactory{

        @Override
        public ArtifactManager managerFor(Run<?, ?> build) {
            return new ArtifactManagerImpl();
        }
        
    }
    
    @TestExtension("testDelete")
    public static class DeleteRunListener extends RunListener<Run>{
        
        private boolean fireDelete = false;
        
        @Override
        public void onDeleted(Run run){
            fireDelete=true;
        }
        
        public boolean isDeleted(){
            return fireDelete;
        }
    }
}
