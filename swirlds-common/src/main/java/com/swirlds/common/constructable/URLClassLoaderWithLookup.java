/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.constructable;

import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;

/**
 * The reason behind the existence of this class is explained here:
 * https://stackoverflow.com/questions/50787116/
 * use-lambdametafactory-to-invoke-one-arg-method-on-class-instance-obtained-from-o
 */
public class URLClassLoaderWithLookup extends URLClassLoader {
	public URLClassLoaderWithLookup(URL[] urls, ClassLoader parent) {
		super(urls, parent);
		defineGimmeLookup();
	}

	public URLClassLoaderWithLookup(URL[] urls) {
		super(urls);
		defineGimmeLookup();
	}

	public MethodHandles.Lookup getLookup() throws ClassNotFoundException, NoSuchFieldException,
			IllegalAccessException {
		return (MethodHandles.Lookup) loadClass("GimmeLookup").getField("lookup").get(null);
	}

	private void defineGimmeLookup() {
		byte[] code = gimmeLookupClassDef();
		defineClass("GimmeLookup", code, 0, code.length);
	}

	private static byte[] gimmeLookupClassDef() {
		return ("\u00CA\u00FE\u00BA\u00BE\0\0\0001\0\21\1\0\13GimmeLookup\7\0\1\1\0\20"
				+ "java/lang/Object\7\0\3\1\0\10<clinit>\1\0\3()V\1\0\4Code\1\0\6lookup\1\0'Ljav"
				+ "a/lang/invoke/MethodHandles$Lookup;\14\0\10\0\11\11\0\2\0\12\1\0)()Ljava/lang"
				+ "/invoke/MethodHandles$Lookup;\1\0\36java/lang/invoke/MethodHandles\7\0\15\14\0"
				+ "\10\0\14\12\0\16\0\17\26\1\0\2\0\4\0\0\0\1\20\31\0\10\0\11\0\0\0\1\20\11\0\5\0"
				+ "\6\0\1\0\7\0\0\0\23\0\3\0\3\0\0\0\7\u00B8\0\20\u00B3\0\13\u00B1\0\0\0\0\0\0")
				.getBytes(StandardCharsets.ISO_8859_1);
	}
}
