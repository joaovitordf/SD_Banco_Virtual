import Storage.Entities.Conta.Conta;
import org.jgroups.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Cliente implements Receiver {
    private JChannel channel;

    public static void main(String[] args) {
        try {
            Cliente cliente = new Cliente();
            cliente.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void start() throws Exception {
        channel=new JChannel();
        channel.setReceiver(this);
        channel.connect("ChatCluster");
        eventLoop();
        channel.close();
    }

    private void eventLoop() {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.println("Digite 'cadastrar', 'alterar', 'remover, 'consultar', 'somarsaldos' ou 'sair' para encerrar:");
            try {
                System.out.print("> "); System.out.flush();
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
            if (object instanceof String) {
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