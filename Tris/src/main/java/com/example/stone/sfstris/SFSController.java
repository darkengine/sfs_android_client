package com.example.stone.sfstris;

import sfs2x.client.SmartFox;

public class SFSController {
	private final static boolean DEBUG_SFS = true;

	private static SmartFox sfsClientInstance;

	public static synchronized SmartFox getSFSClient() {
		if (sfsClientInstance == null) {
			sfsClientInstance = new SmartFox(DEBUG_SFS);
		}
		return sfsClientInstance;
	}
}
