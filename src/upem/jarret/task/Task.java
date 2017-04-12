package upem.jarret.task;

import java.util.HashMap;
import java.util.Map;

public class Task {
	private String JobId;
	private String WorkerVersion;
	private String WorkerURL;
	private String WorkerClassName;
	private String Task;

	public String getJobId() {
		return JobId;
	}

	public String getWorkerVersion() {
		return WorkerVersion;
	}

	public String getWorkerURL() {
		return WorkerURL;
	}

	public String getWorkerClassName() {
		return WorkerClassName;
	}

	public String getTask() {
		return Task;
	}

	public HashMap<String, Object> buildMap() {
		HashMap<String, Object> map = new HashMap<>();
		map.put("JobId", JobId);
		map.put("WorkerVersion", getWorkerVersion());
		map.put("WorkerURL", getWorkerURL());
		map.put("WorkerClassName", getWorkerClassName());
		map.put("Task", getTask());
		return map;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("TaskWorker [JobId=").append(getJobId())
				.append(", WorkerVersion=")
				.append(getWorkerVersion()).append(", WorkerURL=")
				.append(getWorkerURL()).append(", WorkerClassName=")
				.append(getWorkerClassName()).append(", Task=")
				.append(getTask()).append("]");
		return builder.toString();
	}

	public boolean isValid() {
		return JobId != null /*&& WorkerVersionNumber != null*/
				&& WorkerURL != null && WorkerClassName != null
				/*&& JobTaskNumber != null*/;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Task)) {
			return false;
		}
		Task task = (Task) obj;
		return task.JobId.equals(JobId)
				&& task.WorkerVersion.equals(WorkerVersion)
				&& task.WorkerURL.equals(WorkerURL)
				&& task.WorkerClassName.equals(WorkerClassName)
				&& task.Task.equals(Task);
	}
}