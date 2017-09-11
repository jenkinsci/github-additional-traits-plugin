package org.jenkinsci.plugins.github_additional_traits;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCMDescriptor;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.trait.*;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMBuilder;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSourceRequest;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * @author witokondoria
 */
public class AgedRefsStrategyTrait extends SCMSourceTrait {

    private static final Logger LOGGER = Logger.getLogger(AgedRefsStrategyTrait.class.getName());

    private int retentionDays = 0;

    /**
     * Constructor for stapler.
     */
    @DataBoundConstructor
    public AgedRefsStrategyTrait(String retentionDays) {
        if (StringUtils.isBlank(retentionDays)) {
            this.retentionDays = 0;
        }
        try {
            this.retentionDays = Integer.parseInt(retentionDays);
        } catch (NumberFormatException e) {
            this.retentionDays = 0;
        }
    }

    @SuppressWarnings("unused") // used by Jelly EL
    public int getRetentionDays() {
        return this.retentionDays;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        if (this.retentionDays > 0) {
            context.withFilter(new ExcludeOldBranchesSCMHeadFilter(this.retentionDays));
        }
    }

    /**
     * Our descriptor.
     */
    @Extension
    @SuppressWarnings("unused") // instantiated by Jenkins
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Aged refs filterig strategy";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableToBuilder(@NonNull Class<? extends SCMBuilder> builderClass) {
            return GitHubSCMBuilder.class.isAssignableFrom(builderClass);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableToSCM(@NonNull SCMDescriptor<?> scm) {
            return scm instanceof GitSCM.DescriptorImpl;
        }
    }

    /**
     * Filter that excludes references (branches or pull requests) according to its last commmit modification date and the defined retentioDays.
     */
    public static class ExcludeOldBranchesSCMHeadFilter extends SCMHeadFilter {

        private long acceptableDateTimeThreshold;

        public ExcludeOldBranchesSCMHeadFilter(int retentionDays) {
            long now = System.currentTimeMillis();
            this.acceptableDateTimeThreshold = now - (24L * 60 * 60 * 1000 * retentionDays);
        }

        @Override
        public boolean isExcluded(@NonNull SCMSourceRequest scmSourceRequest, @NonNull SCMHead scmHead) throws IOException, InterruptedException {
            if (scmSourceRequest instanceof GitHubSCMSourceRequest) {
                Iterable<GHBranch> branches = ((GitHubSCMSourceRequest) scmSourceRequest).getBranches();
                Iterator<GHBranch> branchIterator = branches.iterator();
                while (branchIterator.hasNext()) {
                    GHBranch branch = branchIterator.next();
                    long branchTS = branch.getOwner().getCommit(branch.getSHA1()).getCommitDate().getTime();
                    if (branch.getName().equals(scmHead.getName())) {
                        return (Long.compare(branchTS, acceptableDateTimeThreshold) < 0);
                    }
                }

                Iterable<GHPullRequest> pulls = ((GitHubSCMSourceRequest) scmSourceRequest).getPullRequests();
                Iterator<GHPullRequest> pullIterator = pulls.iterator();
                while (pullIterator.hasNext()) {
                    GHPullRequest pull = pullIterator.next();
                    if (pull.getHead().equals(scmHead.getName())) {
                        long pullTS = pull.getHead().getCommit().getCommitShortInfo().getCommitDate().getTime();
                        return (Long.compare(pullTS, acceptableDateTimeThreshold) < 0);
                    }
                }
            }
            return false;
        }
    }
}
