package arcs.api;

/** Receives PEC messages. Implemented by PecInnerPort and RemotePecPort classes. */
public interface PecMessageReceiver {

  /** Called with a message intended for the PEC. */
  void onReceivePecMessage(PortableJson message);
}
