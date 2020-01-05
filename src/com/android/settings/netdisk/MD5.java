package com.android.settings.netdisk;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

public class MD5 {

    public static char[] HexChar = {'0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static String toHexString(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            sb.append(HexChar[(b[i] & 0xf0) >>> 4]);
            sb.append(HexChar[b[i] & 0x0f]);
        }
        return sb.toString();
    }

    static public String getFileMD5(String fileName) throws Exception {
        InputStream fis;
        fis = new FileInputStream(fileName);
        byte[] buffer = new byte[1024];
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.reset();
        int numRead = 0;
        while ((numRead = fis.read(buffer)) > 0) {
            md5.update(buffer, 0, numRead);
        }
        fis.close();
        return toHexString(md5.digest());
    }
}
