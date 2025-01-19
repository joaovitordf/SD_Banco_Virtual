import Storage.Entities.Conta.Conta;
import org.jgroups.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;


public class Cliente implements Receiver {
    private JChannel channel;
    private boolean clienteLogado = false;

    public static void main(String[] args) {
        try {
            Cliente cliente = new Cliente();
            cliente.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void start() throws Exception {
        channel = new JChannel("D:\\GitProjects\\SD_Banco_Virtual\\src\\main\\java\\cast.xml");
        //channel = new JChannel();
        channel.setReceiver(this);
        channel.connect("ChatCluster");
        eventLoop();
        channel.close();
    }

    private void eventLoop() {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        try {
            while (!clienteLogado) {
                System.out.println("Digite seu nome:");
                String nomeLogin = in.readLine().toLowerCase();

                System.out.println("Digite sua senha:");
                String senhaLogin = in.readLine().toLowerCase();
                realizarLogin(nomeLogin, senhaLogin);

                Thread.sleep(1000);
            }

            while (true) {
                if (clienteLogado) {
                    System.out.println(
                            "Digite 'cadastrar', 'alterar', 'remover, 'consultar', 'somarsaldos' ou 'sair' para encerrar:");
                    try {
                        System.out.print("> ");
                        System.out.flush();
                        String line = in.readLine().toLowerCase();
                        if (line.startsWith("sair"))
                            break;

                        if (line.startsWith("cadastrar")) {
                            System.out.println("Digite o nome do cliente:");
                            String nome = in.readLine().toLowerCase();

                            System.out.println("Digite a senha do cliente:");
                            String senha = in.readLine().toLowerCase();

                            enviarCadastroCliente(nome, senha);
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
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    private void realizarLogin(String nome, String senha) {
        try {
            // Monta a mensagem de login no formato esperado pelo servidor
            String mensagemLogin = "LOGIN:" + nome + ":" + senha;
            Message msg = new ObjectMessage(null, mensagemLogin);

            System.out.println("[CLIENTE] Enviando solicitação de login...");
            channel.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enviarTransacao(String remetente, String destinatario, double valor) {
        try {
            String mensagemTransacao = "TRANSAÇÃO:" + remetente + ":" + destinatario + ":" + valor;
            Message msg = new ObjectMessage(null, mensagemTransacao);

            System.out.println("[CLIENTE] Solicitando transação de " + valor + " para " + destinatario);
            channel.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enviarCadastroCliente(String nome, String senha) {
        try {
            Conta conta = new Conta(nome, senha);
            ObjectMessage msg = new ObjectMessage(null, conta);

            String mensagem = "Cadastrar Cliente: Nome=" + nome + ", Senha=" + senha;
            System.out.println("[CLIENTE] Dados enviados: " + mensagem);

            // Enviar os dados para o servidor
            channel.send(msg);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enviarConsultaCliente(String nome) {
        try {
            // Envia o nome do cliente para consulta
            String mensagemConsulta = "CONSULTAR:" + nome;
            Message msg = new ObjectMessage(null, mensagemConsulta);

            System.out.println("[CLIENTE] Solicitando dados para o cliente: " + nome);
            channel.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enviarConsultaSomaSaldos() {
        try {
            // Envia uma solicitação ao servidor para somar os saldos de todos os clientes
            String mensagemConsulta = "SOMAR_SALDOS"; // Comando para o servidor
            Message msg = new ObjectMessage(null, mensagemConsulta);

            System.out.println("[CLIENTE] Solicitando soma dos saldos de todos os clientes.");
            channel.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enviarRemocaoCliente(String nome) {
        try {
            // Envia uma solicitação ao servidor para remover o cliente pelo nome
            String mensagemRemocao = "REMOVER:" + nome;
            Message msg = new ObjectMessage(null, mensagemRemocao);

            System.out.println("[CLIENTE] Solicitando remoção do cliente: " + nome);
            channel.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receive(Message msg) {
        try {
            // Recebe a resposta do servidor
            Object object = msg.getObject();
            if (object instanceof Boolean) {
                boolean loginSucesso = (Boolean) object;
                if (loginSucesso) {
                    System.out.println("[CLIENTE] Login realizado com sucesso!");
                    clienteLogado = true;
                } else {
                    System.out.println("[CLIENTE] Falha no login. Nome ou senha incorretos.");
                    clienteLogado = false;
                }
            } else if (object instanceof String) {
                System.out.println("[CLIENTE] Resposta do servidor: " + object);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enviarAlteracaoCliente(String nome, String novaSenha) {
        try {
            // Envia uma solicitação ao servidor para alterar a senha do cliente
            String mensagemAlteracao = "ALTERAR:" + nome + ":" + novaSenha;
            Message msg = new ObjectMessage(null, mensagemAlteracao);

            System.out.println("[CLIENTE] Solicitando alteração da senha para o cliente: " + nome);
            channel.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}