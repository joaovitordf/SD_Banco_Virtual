package View;

import Model.BancoGatewayInterface;
import org.jgroups.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.rmi.Naming;
import java.rmi.RemoteException;

public class Cliente implements Receiver {
    private boolean clienteLogado = false;
    private String nomeLogin = "";
    private String senhaLogin = "";
    private BancoGatewayInterface gateway; // Interface RMI para o gateway

    public static void main(String[] args) {
        try {
            Cliente cliente = new Cliente();
            cliente.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void start() throws Exception {
        // Conectar ao gateway via RMI atraves do servidor coordenador
        gateway = (BancoGatewayInterface) Naming.lookup("rmi://192.168.1.105/BancoGateway");
        System.out.println("[CLIENTE] Conectado ao gateway via RMI.");
        eventLoop();
    }

    private void eventLoop() {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        try {
            while (!clienteLogado) {
                System.out.println("Escolha uma opção:");
                System.out.println("0) Sair");
                System.out.println("1) Cadastro");
                System.out.println("2) Login");

                String escolha = in.readLine().toLowerCase();
                if (escolha.equals("0")) {
                    System.out.println("[CLIENTE] Encerrando aplicação.");
                    return;
                }

                System.out.println("Digite seu nome:");
                nomeLogin = in.readLine().toLowerCase();

                System.out.println("Digite sua senha:");
                senhaLogin = in.readLine().toLowerCase();

                if (escolha.equals("1")) {
                    enviarCadastroCliente(nomeLogin, senhaLogin);
                } else if (escolha.equals("2")) {
                    realizarLogin(nomeLogin, senhaLogin);
                } else {
                    System.out.println("[CLIENTE] Escolha inválida.");
                }
            }

            while (clienteLogado) {
                System.out.println("Digite 'alterar', 'remover', 'consultar', 'somarsaldos', 'transferir' ou 'sair':");
                System.out.print("> ");
                String line = in.readLine().toLowerCase();

                switch (line) {
                    case "alterar":
                        System.out.println("Digite a nova senha:");
                        String novaSenha = in.readLine();
                        enviarAlteracaoCliente(nomeLogin, novaSenha);
                        break;
                    case "remover":
                        enviarRemocaoCliente(nomeLogin);
                        clienteLogado = false;
                        break;
                    case "consultar":
                        enviarConsultaCliente(nomeLogin);
                        break;
                    case "somarsaldos":
                        enviarConsultaSomaSaldos();
                        break;
                    case "transferir":
                        System.out.println("Digite o nome da conta de destino:");
                        String destinatario = in.readLine().toLowerCase();
                        BigDecimal valor;
                        do {
                            System.out.println("Digite o valor a ser transferido (não pode ser negativo):");
                            valor = BigDecimal.valueOf(Double.parseDouble(in.readLine()));
                            if (valor.compareTo(BigDecimal.ZERO) < 0) {
                                System.out.println("[CLIENTE] O valor não pode ser negativo. Tente novamente.");
                            }
                        } while (valor.compareTo(BigDecimal.ZERO) < 0);
                        enviarTransferencia(nomeLogin, destinatario, valor);
                        break;
                    case "sair":
                        clienteLogado = false;
                        eventLoop();
                        return;
                    default:
                        System.out.println("[CLIENTE] Comando inválido.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void realizarLogin(String nome, String senha) {
        try {
            // Realiza o login via RPC
            clienteLogado = gateway.realizarLogin(nome, senha);
            if (clienteLogado) {
                System.out.println("[CLIENTE] Login realizado com sucesso!");
            } else {
                System.out.println("[CLIENTE] Falha no login. Nome ou senha incorretos.");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void enviarCadastroCliente(String nome, String senha) {
        try {
            // Realiza o cadastro via RPC
            boolean cadastroSucesso = gateway.cadastrarCliente(nome, senha);
            if (cadastroSucesso) {
                System.out.println("[CLIENTE] Cliente cadastrado com sucesso!");
            } else {
                System.out.println("[CLIENTE] Falha ao cadastrar cliente. Nome já existe.");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void enviarConsultaCliente(String nome) {
        try {
            // Realiza a consulta via RPC
            String saldo = gateway.consultarSaldo(nome);
            System.out.println("[CLIENTE] Saldo do cliente " + nome + ": " + saldo);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void enviarConsultaSomaSaldos() {
        try {
            // Realiza a consulta da soma dos saldos via RPC
            BigDecimal somaSaldos = gateway.somarSaldos();
            System.out.println("[CLIENTE] Soma dos saldos de todos os clientes: " + somaSaldos);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void enviarRemocaoCliente(String nome) {
        try {
            // Realiza a remoção via RPC
            boolean remocaoSucesso = gateway.removerCliente(nome);
            if (remocaoSucesso) {
                System.out.println("[CLIENTE] Cliente removido com sucesso!");
            } else {
                System.out.println("[CLIENTE] Falha ao remover cliente. Nome não encontrado.");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receive(Message msg) {
        try {
            // Recebe a resposta do servidor
            Object object = msg.getObject();

            if (object instanceof String) {
                String mensagem = (String) object;
                System.out.println("[CLIENTE] Resposta do servidor: " + mensagem);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enviarAlteracaoCliente(String nome, String novaSenha) {
        try {
            // Realiza a alteração via RPC
            boolean alteracaoSucesso = gateway.alterarSenha(nome, novaSenha);
            if (alteracaoSucesso) {
                System.out.println("[CLIENTE] Senha alterada com sucesso!");
            } else {
                System.out.println("[CLIENTE] Falha ao alterar senha. Nome não encontrado.");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void enviarTransferencia(String remetente, String destinatario, BigDecimal valor) {
        try {
            // Realiza a transferência via RPC
            boolean transferenciaSucesso = gateway.realizarTransferencia(remetente, destinatario, valor);
            if (transferenciaSucesso) {
                System.out.println("[CLIENTE] Transferência realizada com sucesso!");
            } else {
                System.out.println("[CLIENTE] Falha ao realizar transferência. Verifique os dados.");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}