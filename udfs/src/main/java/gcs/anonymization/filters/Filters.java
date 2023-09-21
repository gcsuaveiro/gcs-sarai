package gcs.anonymization.filters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.exit;

public class Filters {
    public static String removeIPs(String message){
        // Doesn't catch ports -- Which likely won't need to be removed.
        String regex = "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b";
        String replacement = "x.x.x.x";
        StringBuilder modifiedMessage = new StringBuilder();

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);

        // Uncomment to enable replacing with x.x.x.x
        // return matcher.replaceAll(replacement);

        // Otherwise they're hashed.
        while (matcher.find()) {
            String ipAddress = matcher.group();
            String hashedIP = hashThisString(ipAddress);
            matcher.appendReplacement(modifiedMessage, String.valueOf(hashedIP));        }

        matcher.appendTail(modifiedMessage);
        return modifiedMessage.toString();
    }

    public static String removeIDs(String message){
        String regex = "\\b[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}\\b";
        String replacement = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);

        return matcher.replaceAll(replacement);
    }

    public static String removeUserNames(String message){
        String regex;
        String user,mail;
        boolean hasMail = true;
        if (message.contains("dst_user_name")){
            regex =  "dst_user_name:\\\\\"[^\\\\\"]*";
        }
        else {  //catch-all to avoid needless computing
            return message;
        }

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            System.out.println("Found username");

            String content = matcher.group(0);
            content=content.split("\"")[1];

            user = content.split(" \\(")[0];
            mail = content.split(" \\(")[1].replaceFirst("\\)","");
            // System.out.println(user);
            // System.out.println(mail);
        }
        else{
            return message; // nothing to do
        }

        // ** Hash the found values **
        String userHash = hashThisString(user);
        String mailHash = null;
        if (user.equals(mail)){
            hasMail=false;
        }
        else{
            if (mail.contains("@")){
                mail = mail.split("@")[0]; // user@gmail.com becomes sddfg@gmail.com, still anonymized but can be seen it's an email
            }
            mailHash = hashThisString(mail);
        }

        message = message.replaceAll(user,userHash);
        if (hasMail){
            message = message.replaceAll(mail,mailHash);
        }
        return message;
    }


/*
    This is an application of removeTargetContents specifically for username/email combinations
    since these are the most common (and important) pieces of data to remove, with a specific structure that
    is not always the same.

    This filter covers the three following scenarios:
    fieldName=<email>
    fieldName=<username>
    fieldName=<username> (<email>)
 */

public static String removeUserNames_v2(String message,String field_name){

        String regex;
        String user="",mail="";
        boolean twoPart = false;
        if (message.contains(field_name)){
            regex =  field_name+"=(.*?)(\\t|\\\\t)";
        }
        else {
            return message;
        }

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            System.out.println("FOUND");

            String content = matcher.group(0);
            System.out.println(content);

            content=content.split("=")[1];

            if(content.contains(" (")) {
                System.out.println("Has two parts");
                twoPart = true;
                content = content.replaceAll("\t|\\t|\\\\t", "");

                user = content.split(" \\(")[0];
                mail = content.split(" \\(")[1].replaceAll("\\)", "").replaceAll("\\) ","").replaceAll(" $","");
                System.out.println(user);
                System.out.println(mail);

            }
            else {
                System.out.println("One part");
                mail = content.replaceAll("\\t", "");
            }

            //System.out.println("---");
            //System.out.println(user);
            //System.out.println(mail);
        }
        else {
            return message; // nothing to do
        }

        String userHash = null;
        String mailHash = null;

        if (!mail.equals("")) {

            mail = mail.split("@")[0]; // user@gmail.com becomes sddfg@gmail.com, still anonymized but can be seen it's an email
            mailHash = hashThisString(mail);
            message = message.replaceAll(mail,mailHash);
            System.out.println("after mail replace");
            System.out.println(message);
            System.out.println(mail);

        }
        if (!user.equals("")) {  // has user
            user = user.split("@")[0]; // Just in case
            userHash = hashThisString(user);
            message = message.replaceAll(escapeString(user), userHash);

        }

        return message;
    }

    /*
        This will remove the contents of a given field in the ENTIRE message
        if there is person_name="john secure" then all instances of "john secure"
        will be removed from the message.
    */

    public static String removeFieldContents(String message,String field_name){
        // Filter doesn't apply.
        if(!message.contains(field_name)){
            return message;
        }

        String regex;
        String target = "";
       // regex = "\\\\t"+field_name + "=(.*?)\\\\t"; // Can't handle edge cases (first or last field in message)
       //regex = "(\\\\t|\\|)("+field_name+"=(.*?))(\\t|$)";
        regex = "(\\t|\\\\t|\\\\\\\\|\\|)("+field_name+"(.*?))(\\t|$|\\\\t)";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            //System.out.println("FOUND");

            String content = matcher.group(0);
            System.out.println(content);
            content=content.split(field_name+"=")[1];
            target = content.replaceAll("\t", "");

            System.out.println("---");
            System.out.println(target);

        } else {
                return message; // nothing to do
        }


        String targetHash;

        if (!target.equals("") && !target.equals("-")) {
            System.out.println("hashing");

            targetHash = hashThisString(target);
            message = message.replaceAll(escapeString(target),targetHash);


        }

        return message;
    }

// Needed for edge cases surrounding replaceAll() that make it fail silently.
    public static String escapeString(String input){
       String[] forbidden = {")","(",".","/","+","*","?","^","$","[","]","{","}","|","-","&","%","#","@",";",":","="};
       for (String ch : forbidden)
            if(input.contains(ch)){
                input = input.replaceAll("\\"+ch,"\\\\"+ch); // IMPORTANT: Bug in IntelliJ marks this as bug
                                                                              // DO NOT "FIX". It is correct.
            }
       return input;
    }

    // string goes in, hash goes out
    public static String hashThisString(String input)
    {
        try {
            // Create a SHA-3-256 MessageDigest instance
            MessageDigest sha3Digest = MessageDigest.getInstance("SHA3-256");

            // Convert the input string to bytes
            byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);

            // Generate the hash value
            byte[] hashBytes = sha3Digest.digest(inputBytes);

            // Convert the hash bytes to hexadecimal format
            StringBuilder hexString = new StringBuilder();
            for (byte hashByte : hashBytes) {
                String hex = Integer.toHexString(0xff & hashByte);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            // Print the hash value
            //System.out.println("SHA-3 Hash: " + hexString.toString());
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            exit(1);
        }
        return "compiler stop complaining";
    }

    public static String apache_SAMLFilter(String message){
        if(!message.contains("SAMLResponse")){
            return message;
        }

        String regex = "(\\?|\\%22\\%2C|\\&)(SAMLResponse(.*?)|SAMLRequest(.*?))(&|$| )";
        String target = "";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {

            String content = matcher.group(0);
            System.out.println(content);
            content=content.split("SAMLResponse=")[1];
            target = content.replaceAll("&", "");

        } else {
            return message; // nothing to do
        }


        String targetHash;

        if (!target.equals("") && !target.equals("-")) {
            System.out.println("hashing");
            //targetHash = hashThisString(target);
            message = message.replaceAll(escapeString(target),"REDACTED");


        }

        return message;
    }
    public static String apache_removeFieldContents(String message,String field_name){
        // Filter doesn't apply.
        if(!message.contains(field_name)){
            return message;
        }

        String regex;
        String target = "";
        regex = "(\\?|%22%2C|&)("+field_name+"(.*?))(&|$| |%22%2C%22)";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            //System.out.println("FOUND");

            String content = matcher.group(0);
            System.out.println(content);
            content=content.split(field_name+"=")[1];
            target = content.replaceAll("\t", "");

            System.out.println("---");
            System.out.println(target);

        } else {
            return message; // nothing to do
        }


        String targetHash;

        if (!target.equals("") && !target.equals("-")) {
            System.out.println("hashing");

            targetHash = hashThisString(target);
            message = message.replaceAll(escapeString(target),targetHash);


        }

        return message;
    }


}
