import Model.Transferencia;
import Storage.Entities.Conta.Conta;
import org.jgroups.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;


public class Cliente implements Receiver {
    private JChannel channel;
    private boolean clienteLogado = false;
    private int idContaOrigem = -1;
    private int idContaDestino = -1;
    private String nomeLogin = "";
    private String senhaLogin = "";

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
            String mensagemConsulta = "CONSULTAR_SALDO:" + nome;
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
                String mensagem = (String) object;
                System.out.println(mensagem);

                if (mensagem.startsWith("[SERVIDOR] Conta encontrada:")) {
                    String[] partes = mensagem.split("="); // Divide a string por "="
                    if (partes.length == 2) {
                        try {
                            int idConta = Integer.parseInt(partes[1].trim());
                            if (mensagem.contains("origem")) {
                                idContaOrigem = idConta;
                                System.out.println("[CLIENTE] ID da conta de origem: " + idContaOrigem);
                            } else if (mensagem.contains("destino")) {
                                idContaDestino = idConta;
                                System.out.println("[CLIENTE] ID da conta de destino: " + idContaDestino);
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("[CLIENTE] Erro ao processar o ID da conta: " + e.getMessage());
                        }
                    }
                } else {
                    System.out.println("[CLIENTE] Resposta do servidor: " + mensagem);
                }
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

    // Método para enviar a solicitação de transferência
    private void enviarTransferencia(String remetente, String destinatario, BigDecimal valor) {
        try {
            // Enviar solicitação ao servidor para buscar a conta de origem
            String mensagemOrigem = "CONSULTAR_ID_ORIGEM:" + remetente;
            Message msgOrigem = new ObjectMessage(null, mensagemOrigem);
            channel.send(msgOrigem);

            // Enviar solicitação ao servidor para buscar a conta de destino
            String mensagemDestino = "CONSULTAR_ID_DESTINO:" + destinatario;
            Message msgDestino = new ObjectMessage(null, mensagemDestino);
            channel.send(msgDestino);

            Thread.sleep(1000);

            Transferencia transferencia = new Transferencia();
            transferencia.setIdOrigem(idContaOrigem);
            transferencia.setIdDestino(idContaDestino);
            transferencia.setValor(valor);

            // Envia o pedido de transferência para o servidor
            ObjectMessage msg = new ObjectMessage(null, transferencia);

            System.out.println("[CLIENTE] Solicitando transferência de " + valor + " de " + remetente + " para " + destinatario);
            channel.send(msg);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}