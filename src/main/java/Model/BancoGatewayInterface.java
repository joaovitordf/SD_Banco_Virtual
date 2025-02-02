import java.math.BigDecimal;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BancoGatewayInterface extends Remote {
    // Método para realizar login
    boolean realizarLogin(String nome, String senha) throws RemoteException;

    // Método para cadastrar um novo cliente
    boolean cadastrarCliente(String nome, String senha) throws RemoteException;

    // Método para consultar o saldo de um cliente
    String consultarSaldo(String nome) throws RemoteException;

    // Método para somar os saldos de todos os clientes
    BigDecimal somarSaldos() throws RemoteException;

    // Método para remover um cliente
    boolean removerCliente(String nome) throws RemoteException;

    // Método para alterar a senha de um cliente
    boolean alterarSenha(String nome, String novaSenha) throws RemoteException;

    // Método para realizar uma transferência entre contas
    boolean realizarTransferencia(String remetente, String destinatario, BigDecimal valor) throws RemoteException;
}