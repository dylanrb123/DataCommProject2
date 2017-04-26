/**
 * Possible states of the TCP connection
 */
public enum TcpConnectionState {
    CLOSED,
    SYN_SENT,
    LISTEN,
    SYN_RECEIVED,
    ESTABLISHED
}
