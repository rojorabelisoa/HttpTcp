package fr.upem.net.tcp.http;

public class HTTPStateException extends HTTPException {

	private static final long serialVersionUID = -1810727803680020453L;

	public HTTPStateException() {
		super();
	}

	public HTTPStateException(String s) {
		super(s);
	}

	public static void ensure(boolean b, String string)
			throws HTTPStateException {
		if (!b)
			throw new HTTPStateException(string);

	}
}