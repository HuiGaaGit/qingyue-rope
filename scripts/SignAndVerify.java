import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.*;
import java.util.jar.*;

/** Uses the existing public AOSP TEST key. Never generates a credential or private release key. */
public final class SignAndVerify {
    public static void main(String[] args)throws Exception {
        if(args[0].equals("sign")) {
            PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Paths.get(args[3]))));
            X509Certificate cert;
            try(InputStream in=new FileInputStream(args[4])){cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);}
            ApkSigner.SignerConfig signer=new ApkSigner.SignerConfig.Builder("aosp-public-test-key",key,Collections.singletonList(cert)).build();
            new ApkSigner.Builder(Collections.singletonList(signer)).setInputApk(new File(args[1])).setOutputApk(new File(args[2])).setMinSdkVersion(26)
                    .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(false).setV4SigningEnabled(false).build().sign();
        }
        String apk=args[0].equals("sign")?args[2]:args[1];
        ApkVerifier.Result result=new ApkVerifier.Builder(new File(apk)).setMinCheckedPlatformVersion(26).build().verify();
        for(Object e:result.getErrors())System.out.println("ERROR "+e);
        for(Object e:result.getWarnings())System.out.println("WARNING "+e);
        System.out.println("verified="+result.isVerified()+" v1="+result.isVerifiedUsingV1Scheme()+" v2="+result.isVerifiedUsingV2Scheme());
        if(!result.isVerified())throw new IllegalStateException("APK signature verification failed");
        byte[] fingerprint=MessageDigest.getInstance("SHA-256").digest(result.getSignerCertificates().get(0).getEncoded());
        StringBuilder hex=new StringBuilder();for(byte b:fingerprint)hex.append(String.format("%02x",b));
        System.out.println("certificate-sha256="+hex+" (public AOSP test certificate)");
        int signedEntries=0;
        try(JarFile jar=new JarFile(apk,true)) {
            Enumeration<JarEntry> entries=jar.entries();byte[] buffer=new byte[16384];
            while(entries.hasMoreElements()) {
                JarEntry entry=entries.nextElement();
                if(entry.isDirectory()||entry.getName().startsWith("META-INF/"))continue;
                try(InputStream in=jar.getInputStream(entry)){while(in.read(buffer)!=-1){}}
                java.security.cert.Certificate[] certs=entry.getCertificates();
                if(certs==null||certs.length==0||!Arrays.equals(certs[0].getEncoded(),result.getSignerCertificates().get(0).getEncoded()))
                    throw new SecurityException("Missing/mismatched JAR v1 signature: "+entry.getName());
                signedEntries++;
            }
        }
        System.out.println("JAR-v1 independently verified on "+signedEntries+" payload entries. APK verifier prefers v2 at minSdk26.");
    }
}
