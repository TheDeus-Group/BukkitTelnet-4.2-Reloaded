package me.StevenLawson.BukkitTelnet;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import me.StevenLawson.BukkitTelnet.session.ClientSession;

public class SocketListener extends Thread
{
    public static long LISTEN_THRESHOLD_MILLIS = 10000;
    private final ServerSocket serverSocket;
    private final List<ClientSession> clientSessions;
    private final Map<InetAddress, Long> recentIPs;

    public SocketListener(ServerSocket serverSocket)
    {
        this.serverSocket = serverSocket;
        this.clientSessions = new ArrayList<ClientSession>();
        this.recentIPs = new HashMap<InetAddress, Long>();
    }

    @Override
    public void run()
    {
        while (!serverSocket.isClosed())
        {
            final Socket clientSocket;

            try
            {
                clientSocket = serverSocket.accept();
            }
            catch (IOException ex)
            {
                continue;
            }

            // Remove old entries
            final Iterator<Entry<InetAddress, Long>> it = recentIPs.entrySet().iterator();
            Entry<InetAddress, Long> entry;
            while (it.hasNext())
            {
                if (it.next().getValue() + LISTEN_THRESHOLD_MILLIS < System.currentTimeMillis())
                {
                    it.remove();
                }
            }

            final InetAddress addr = clientSocket.getInetAddress();
            if (addr == null)
            {
                return; // Socket is not connected
            }

            // Connect Threshold
            if (recentIPs.containsKey(addr))
            {
                recentIPs.put(addr, System.currentTimeMillis());

                try
                {
                    final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
                    writer.write("Connection throttled. Please wait a minute and try again.\r\n");
                    writer.flush();
                }
                catch (IOException ignored)
                {
                }

                try
                {
                    clientSocket.close();
                }
                catch (IOException ignored)
                {
                }

                continue;
            }

            recentIPs.put(addr, System.currentTimeMillis());

            final ClientSession clientSession = new ClientSession(clientSocket);
            clientSessions.add(clientSession);
            clientSession.start();

            removeDisconnected();
        }
    }

    private void removeDisconnected()
    {
        final Iterator<ClientSession> it = clientSessions.iterator();

        while (it.hasNext())
        {
            final ClientSession session = it.next();

            if (!session.syncIsConnected())
            {
                TelnetLogAppender.getInstance().removeSession(session);
                it.remove();
            }
        }
    }

    public void triggerPlayerListUpdates(final String playerListData)
    {
        final Iterator<ClientSession> it = clientSessions.iterator();

        while (it.hasNext())
        {
            final ClientSession session = it.next();
            if (session != null)
            {
                session.syncTriggerPlayerListUpdate(playerListData);
            }
        }
    }

    public void stopServer()
    {
        try
        {
            serverSocket.close();
        }
        catch (IOException ex)
        {
            TelnetLogger.severe(ex);
        }

        for (ClientSession session : clientSessions)
        {
            session.syncTerminateSession();
        }

        clientSessions.clear();

    }
}
