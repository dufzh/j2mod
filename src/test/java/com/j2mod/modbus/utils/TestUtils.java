/*
 * Copyright 2002-2016 jamod & j2mod development teams
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.j2mod.modbus.utils;

import com.fazecast.jSerialComm.SerialPort;
import com.j2mod.modbus.util.Logger;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * This class is a collection of utility methods used by all test classes
 *
 * @author Steve O'Hara (4energy)
 * @version 2.0 (March 2016)
 *
 */
public class TestUtils {

    private static final Logger logger = Logger.getLogger(TestUtils.class);
    private static final int DEFAULT_BUFFER_SIZE = 32 * 1024;

    /**
     * This method will extract the appropriate Modbus master tool into the
     * temp folder so that it can be used later
     *
     * @throws Exception
     */
    public static void loadModPollTool() throws Exception {

        // Load the resource from the library

        String osName = System.getProperty("os.name");

        // Work out the correct name

        String exeName;
        if (osName.matches("(?is)windows.*")) {
            osName = "win32";
            exeName = "modpoll.exe";
        }
        else if (osName.matches("(?is)mac.*")) {
            osName = "macosx";
            exeName = "modpoll";
        }
        else {
            osName = "linux";
            exeName = "modpoll";
        }

        // Check to see if we already have the library available

        File nativeFile = new File(getTemporaryDirectory(), exeName);
        if (!nativeFile.exists()) {

            // Copy the library to the temporary folder

            InputStream in = null;
            String resourceName = String.format("/com/j2mod/modbus/native/%s/%s", osName, exeName);

            try {
                in = SerialPort.class.getResourceAsStream(resourceName);
                if (in == null) {
                    throw new Exception(String.format("Cannot find resource [%s]", resourceName));
                }
                pipeInputToOutputStream(in, nativeFile, false);

                // Set the correct privileges

                if (!nativeFile.setWritable(true, true)) {
                    logger.warn("Cannot set RxTx library to be writable");
                }
                if (!nativeFile.setReadable(true, false)) {
                    logger.warn("Cannot set RxTx library to be readable");
                }
            }
            catch (Exception e) {
                throw new Exception(String.format("Cannot locate native library [%s] - %s", exeName, e.getMessage()));
            }
            finally {
                if (in != null) {
                    try {
                        in.close();
                    }
                    catch (IOException e) {
                        logger.error("Cannot close stream - %s", e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Returns a full path name of a suitable temporary filename
     *
     * @return String
     */
    public static String getTemporaryDirectory() {
        return System.getProperty("java.io.tmpdir");
    }

    /**
     * Returns a full path name of a suitable temporary filename
     *
     * @return File
     */
    public static File getTemporaryFile() {
        return new File(getTemporaryFilename(null));
    }

    /**
     * Returns a full path name of a suitable temporary filename using the
     * extension provided.  If sExtension is null then .tmp is used
     *
     * @param extension Extension to give the file
     *
     * @return File
     */
    public static File getTemporaryFile(String extension) {
        return new File(getTemporaryFilename(extension));
    }

    /**
     * Returns a full path name of a suitable temporary filename
     *
     * @return String
     */
    public static String getTemporaryFilename() {
        return getTemporaryFilename(null);
    }

    /**
     * Returns a full path name of a suitable temporary filename using the
     * extension provided.  If sExtension is null then .tmp is used
     *
     * @param extension Extension to give the file
     *
     * @return String
     */
    public static String getTemporaryFilename(String extension) {
        return getTemporaryDirectory() + File.separator + getTemporaryFilenameOnly(extension);
    }

    /**
     * Returns a temporary filename only using the extension if provided. if the provided value is null then .tmp is used
     *
     * @param extension Extension to give the file
     *
     * @return a {@link java.lang.String} object
     */
    public static String getTemporaryFilenameOnly(String extension) {
        String returnValue;
        if (extension != null) {
            returnValue = getTemporaryName() + '.' + extension.trim();
        }
        else {
            returnValue = getTemporaryName() + ".tmp";
        }

        return returnValue;
    }

    /**
     * Returns a temporary name which should be unique to this thread
     *
     * @return a {@link java.lang.String} object
     */
    public static String getTemporaryName() {
        return "j2mode-" + Thread.currentThread().getId() + '-' + System.nanoTime();
    }

    /**
     * Convenient way of sending data from an input stream to an output file
     * in the most efficient way possible
     *
     * @param in           Input stream to read from
     * @param fileOut      Output file to write to
     * @param ignoreErrors True if this method must not throw any socket errors
     *
     * @throws IOException if an error occurs
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void pipeInputToOutputStream(InputStream in, File fileOut, boolean ignoreErrors) throws IOException {
        if (fileOut == null) {
            logger.error("The output filename doesn't exist or is invalid");
            if (!ignoreErrors) {
                throw new IOException("The output filename doesn't exist or is invalid");
            }
        }
        else {

            // Create the parentage for the folders if they don't exist

            File parent = fileOut.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            OutputStream fileStream = null;
            try {
                fileStream = new FileOutputStream(fileOut);
                pipeInputToOutputStream(in, fileStream, true, ignoreErrors);
            }
            catch (IOException e) {
                if (fileStream != null) {
                    try {
                        fileStream.close();
                    }
                    catch (IOException ex) {
                        logger.error("Cannot close stream - %s", ex.getMessage());
                    }
                }
                if (!ignoreErrors) {
                    throw e;
                }
            }
        }
    }

    /**
     * Convenient way of sending data from an input stream to an output stream
     * in the most efficient way possible
     * If the bCloseOutput flag is false, then the output stream remains open
     * so that further writes can be made to the stream
     *
     * @param in           Input stream to read from
     * @param out          Output stream to write to
     * @param closeOutput  True if the output stream should be closed on exit
     * @param ignoreErrors True if this method must not throw any socket errors
     *
     * @throws IOException if an error occurs
     */
    public static void pipeInputToOutputStream(InputStream in, OutputStream out, boolean closeOutput, boolean ignoreErrors) throws IOException {

        OutputStream bufferedOut = out;
        InputStream bufferedIn = in;

        if (in != null && out != null) {
            try {
                // Buffer the streams if they aren't already

                if (!bufferedOut.getClass().equals(BufferedOutputStream.class)) {
                    bufferedOut = new BufferedOutputStream(bufferedOut, DEFAULT_BUFFER_SIZE);
                }
                if (!bufferedIn.getClass().equals(BufferedInputStream.class)) {
                    bufferedIn = new BufferedInputStream(bufferedIn, DEFAULT_BUFFER_SIZE);
                }

                // Push the data

                int iTmp;
                while ((iTmp = bufferedIn.read()) != -1) {
                    bufferedOut.write((byte)iTmp);
                }
                bufferedOut.flush();
                out.flush();
            }
            catch (IOException e) {
                if (!ignoreErrors && !(e instanceof java.net.SocketException)) {
                    logger.error(e.getMessage());
                    throw e;
                }
                else {
                    logger.debug(e.getMessage());
                }
            }
            finally {
                bufferedIn.close();
                if (closeOutput) {
                    bufferedOut.close();
                }
            }
        }
    }

    /**
     * Runs a command line task and returns the screen output or throws and
     * error if something bad happened
     *
     * @param command Command to run
     *
     * @return Screen output
     *
     * @throws Exception
     */
    public static String execToString(String command) throws Exception {

        // Prepare the command line

        CommandLine commandline = CommandLine.parse(command);

        // Prepare the output stream

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

        // Prepare the executor

        DefaultExecutor exec = new DefaultExecutor();
        exec.setExitValues(null);
        exec.setStreamHandler(streamHandler);
        exec.setWatchdog(new ExecuteWatchdog(5000));

        // Execute the command
        try {
            exec.execute(commandline);
            return (outputStream.toString());
        }
        catch (Exception e) {
            throw new Exception(String.format("%s - %s", outputStream.toString(), e.getMessage()));
        }
    }

    /**
     * Returns the last adapter it finds that is not a loopback
     *
     * @return Adapter to use
     */
    public static List<NetworkInterface> getNetworkAdapters() {
        List<NetworkInterface> returnValue = new ArrayList<NetworkInterface>();
        try {

            // Loop round all the adapters

            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {

                // Get the MAC address if it exists

                NetworkInterface network = networkInterfaces.nextElement();
                byte[] mac = network.getHardwareAddress();
                if (!network.isLoopback() && !network.isVirtual() && network.isUp() && mac != null && mac.length > 0 && network.getInterfaceAddresses() != null) {
                    returnValue.add(network);
                    logger.debug("Current MAC address : %s (%s)", returnValue, network.getDisplayName());
                }
            }
        }
        catch (Exception e) {
            logger.error("Cannot determine the local MAC address - %s", e.getMessage());
        }
        return returnValue;
    }


    /**
     * Returns the first real IP address it finds
     * @return Real IP address or null if nothing available
     */
    public static String getFirstIp4Address() {

        // Get all the physical adapters

        List<NetworkInterface> adapters = getNetworkAdapters();
        if (adapters.size() > 0) {
            for (NetworkInterface adapter : adapters) {

                // Loop through all the addresses

                Enumeration<InetAddress> addresses = adapter.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    // Only interested in non-loopback and IPv4 types

                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        }
        return null;
    }

}
