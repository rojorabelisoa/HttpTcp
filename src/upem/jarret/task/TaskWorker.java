package upem.jarret.task;

import java.net.MalformedURLException;

import upem.jarret.worker.Worker;
import upem.jarret.worker.WorkerFactory;

public class TaskWorker extends Task {
	public Worker getWorker() throws MalformedURLException,
			ClassNotFoundException, IllegalAccessException,
			InstantiationException {
		return WorkerFactory.getWorker(getWorkerURL(), getWorkerClassName());
	}
}
