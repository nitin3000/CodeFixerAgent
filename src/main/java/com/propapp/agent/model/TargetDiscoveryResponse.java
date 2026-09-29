package com.propapp.agent.model;

import java.util.List;

public class TargetDiscoveryResponse {
    private List<FailureTarget> failures;

    public List<FailureTarget> getFailures() { return failures; }
    public void setFailures(List<FailureTarget> failures) { this.failures = failures; }

    public static class FailureTarget {
        private String filePath;
        private String errorReason;

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getErrorReason() { return errorReason; }
        public void setErrorReason(String errorReason) { this.errorReason = errorReason; }
    }
}
