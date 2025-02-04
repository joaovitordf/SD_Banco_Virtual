import Model.BancoGatewayInterface;
import org.jgroups.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.io.File;

public class Cliente implements Receiver {
    private JChannel channel;
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
        // Conectar ao gateway via RMI
        // VAI SER NECESSARIO ALTERAR A LINHA ABAIXO PARA MULTIPLOS SERVIDORES!
        gateway = (BancoGatewayInterface) Naming.lookup("rmi://localhost/BancoGateway");
        System.out.println("[CLIENTE] Conectado ao gateway via RMI.");

        channel = new JChannel(retornaDiretorio("cast.xml"));
        channel.setReceiver(this);
        channel.connect("ChatCluster");
        eventLoop();
        channel.close();
    }

    public static String retornaDiretorio(String document) {
        // Obtém o diretório atual onde o programa está rodando
        String dirPath = new File("").getAbsolutePath();

        return dirPath + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + document;
    }

    private void eventLoop() {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        try {
            while (!clienteLogado) {
                System.out.println("Digite seu nome:");
                nomeLogin = in.readLine().toLowerCase();

                System.out.println("Digite sua senha:");
                senhaLogin = in.readLine().toLowerCase();
                realizarLogin(nomeLogin, senhaLogin);

                Thread.sleep(1000);

                if (!clienteLogado) {
                    System.out.println("[CLIENTE] Deseja cadastrar um novo usuário com os dados informados? (s/n)");
                    String resposta = in.readLine().toLowerCase();
                    if (resposta.equals("s")) {
                        enviarCadastroCliente(nomeLogin, senhaLogin);
                    }
                }
            }

            while (true) {
                if (clienteLogado) {
                    System.out.println(
                            "Digite 'cadastrar', 'alterar', 'remover', 'consultar', 'somarsaldos', 'transferir' ou 'sair' para encerrar:");
                    try {
                        System.out.print("> ");
                        System.out.flush();
                        String line = in.readLine().toLowerCase();
                        if (line.startsWith("sair"))
                            break;

                        if (line.startsWith("cadastrar")) {
                            System.out.println("Digite o nome do cliente:");
                            nomeLogin = in.readLine().toLowerCase();

                            System.out.println("Digite a senha do cliente:");
                            senhaLogin = in.readLine().toLowerCase();

                            enviarCadastroCliente(nomeLogin, senhaLogin);
                        }

                        if (line.startsWith("remover")) {
                            System.out.println("Digite o nome do cliente:");
                            String nome = in.readLine();

                            enviarRemocaoCliente(nome);
                        }

                        if (line.startsWith("alterar")) {
                            System.out.println("Digite o nome do cliente:");
                            String nome = in.readLine();

                            System.out.println("Digite a nova senha do cliente:");
                            String novaSenha = in.readLine();

                            enviarAlteracaoCliente(nome, novaSenha);
                        }

                        if (line.startsWith("consultar")) {
                            System.out.println("Digite o nome do cliente:");
                            String nome = in.readLine();

                            enviarConsultaCliente(nome);
                        }

                        if (line.startsWith("somarsaldos")) {
                            enviarConsultaSomaSaldos();
                        }

                        if (line.startsWith("transferir")) {
                            System.out.println("Digite o nome da conta de destino:");
                            String destinatario = in.readLine().toLowerCase();

                            System.out.println("Digite o valor a ser transferido:");
                            BigDecimal valor = BigDecimal.valueOf(Double.parseDouble(in.readLine()));

                            enviarTransferencia(nomeLogin, destinatario, valor);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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