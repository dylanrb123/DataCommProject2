//******************************************************************************
//
// File:    UdpDisrupter.java
// Package: ---
// Unit:    Class UdpDisrupter
//
// This Java source file is copyright (C) 2016 by Alan Kaminsky. All rights
// reserved. For further information, contact the author, Alan Kaminsky, at
// ark@cs.rit.edu.
//
// This Java source file is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by the Free
// Software Foundation; either version 3 of the License, or (at your option) any
// later version.
//
// This Java source file is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
// details.
//
// You may obtain a copy of the GNU General Public License on the World Wide Web
// at http://www.gnu.org/licenses/gpl.html.
//
//******************************************************************************

import edu.rit.util.Random;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Class UdpDisrupter is a main program that disrupts a series of UDP messages.
 * The UDP Disrupter has two mailboxes designated A and B. Mailbox A receives
 * messages from <I>inhost_A:inport_A</I> and forwards them to
 * <I>outhost_A:outport_A</I>. Mailbox B receives messages from
 * <I>inhost_B:inport_B</I> and forwards them to <I>outhost_B:outport_B</I>.
 * Before forwarding, each datagram is delayed from 0 to <I>delay</I>
 * milliseconds chosen at random. In addition, a fraction <I>drop</I> of the
 * datagrams chosen at random are dropped. The contents of the datagrams are not
 * altered.
 * <P>
 * <I>Note:</I> The recipient at <I>outhost_A:outport_A</I> and the recipient at
 * <I>outhost_B:outport_B</I> will see datagrams coming from the UDP Disrupter,
 * not from the original sender.
 * <P>
 * Usage: <TT>java UdpDisrupter <I>inhost_A</I> <I>inport_A</I> <I>outhost_A</I>
 * <I>outport_A</I> <I>inhost_B</I> <I>inport_B</I> <I>outhost_B</I>
 * <I>outport_B</I> <I>delay</I> <I>drop</I></TT>
 *
 * @author  Alan Kaminsky
 * @version 15-Mar-2016
 */
public class UdpDisrupter
{

    /**
     * Global variables.
     */
    private static int delay;
    private static double drop;
    private static ScheduledExecutorService pool;
    private static long packetNum;

    /**
     * Returns the next packet number.
     */
    private static synchronized long nextPacketNum()
    {
        return ++ packetNum;
    }

    /**
     * Main program.
     */
    public static void main
    (String[] args)
            throws Exception
    {
        // Parse command line arguments.
        if (args.length != 10) usage();
        String inhost_A = args[0];
        int inport_A = Integer.parseInt (args[1]);
        String outhost_A = args[2];
        int outport_A = Integer.parseInt (args[3]);
        String inhost_B = args[4];
        int inport_B = Integer.parseInt (args[5]);
        String outhost_B = args[6];
        int outport_B = Integer.parseInt (args[7]);
        delay = Integer.parseInt (args[8]);
        drop = Double.parseDouble (args[9]);

        // Set up addresses.
        InetSocketAddress in_A = new InetSocketAddress (inhost_A, inport_A);
        InetSocketAddress out_A = new InetSocketAddress (outhost_A, outport_A);
        InetSocketAddress in_B = new InetSocketAddress (inhost_B, inport_B);
        InetSocketAddress out_B = new InetSocketAddress (outhost_B, outport_B);

        // Set up mailboxes.
        DatagramSocket mailbox_A = new DatagramSocket (in_A);
        DatagramSocket mailbox_B = new DatagramSocket (in_B);

        // Set up thread pool.
        pool = Executors.newScheduledThreadPool (1);

        // Process incoming datagrams.
        new Handler (mailbox_A, mailbox_B, out_A) .start();
        new Handler (mailbox_B, mailbox_A, out_B) .start();
    }

    /**
     * Print a usage message and exit.
     */
    private static void usage()
    {
        System.err.println ("Usage: java UdpDisrupter <inhost_A> <inport_A> <outhost_A> <outport_A> <inhost_B> <inport_B> <outhost_B> <outport_B> <delay> <drop>");
        System.exit (1);
    }

    /**
     * Thread for receiving packets from a mailbox.
     */
    private static class Handler
            extends Thread
    {
        private DatagramSocket inMailbox;
        private DatagramSocket outMailbox;
        private InetSocketAddress outAddress;

        private byte[] payload = new byte [1024];
        private DatagramPacket packet =
                new DatagramPacket (payload, payload.length);
        private Random prng = new Random (System.currentTimeMillis());

        public Handler
                (DatagramSocket inMailbox,
                 DatagramSocket outMailbox,
                 InetSocketAddress outAddress)
                throws IOException
        {
            this.outAddress = outAddress;
            this.inMailbox = inMailbox;
            this.outMailbox = outMailbox;
        }

        public void run()
        {
            try
            {
                for (;;)
                {
                    // Receive a datagram.
                    inMailbox.receive (packet);
                    long packetNum = nextPacketNum();
                    synchronized (System.out)
                    {
                        System.out.printf ("Receive [%d] from %s%n",
                                packetNum, packet.getSocketAddress());
                    }

                    // Decide whether to drop the packet.
                    if (prng.nextDouble() < drop)
                    {
                        synchronized (System.out)
                        {
                            System.out.printf ("\t\t\t\t\tDrop [%d]%n",
                                    packetNum);
                        }
                    }

                    // Forward the packet after a random delay.
                    else
                    {
                        byte[] buf = new byte [packet.getLength()];
                        System.arraycopy (payload, 0, buf, 0, buf.length);
                        pool.schedule
                                (new Forwarder
                                                (outMailbox, outAddress, buf, packetNum),
                                        prng.nextInt (delay), TimeUnit.MILLISECONDS);
                    }
                }
            }
            catch (IOException exc)
            {
                exc.printStackTrace (System.err);
                System.exit (1);
            }
        }
    }

    /**
     * Runnable object for forwarding a packet.
     */
    private static class Forwarder
            implements Runnable
    {
        private DatagramSocket outMailbox;
        private InetSocketAddress outAddress;
        private DatagramPacket packet;
        private long packetNum;

        public Forwarder
                (DatagramSocket outMailbox,
                 InetSocketAddress outAddress,
                 byte[] buf,
                 long packetNum)
                throws IOException
        {
            this.outMailbox = outMailbox;
            this.outAddress = outAddress;
            this.packet = new DatagramPacket (buf, buf.length, outAddress);
            this.packetNum = packetNum;
        }

        public void run()
        {
            try
            {
                outMailbox.send (packet);
                synchronized (System.out)
                {
                    System.out.printf ("\t\t\t\t\tSend [%d] to %s%n",
                            packetNum, outAddress);
                }
            }
            catch (IOException exc)
            {
                exc.printStackTrace (System.err);
                System.exit (1);
            }
        }
    }

}
