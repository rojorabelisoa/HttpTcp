package upem.jarret.task;

public class NoTaskException extends Exception {
	private final long until;
	public NoTaskException(int intValue) {
		until = System.currentTimeMillis() + intValue;
	}
	public NoTaskException() {
		this(0);
	}
	public long getUntil() {
		return until;

	}

}
