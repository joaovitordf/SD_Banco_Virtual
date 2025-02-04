package Model;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClienteCallback extends Remote {
    void notificarAtualizacao(String mensagem) throws RemoteException;
}
