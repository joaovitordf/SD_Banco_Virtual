import Storage.Entities.Conta.Conta;
import org.jgroups.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Cliente {
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
        channel.connect("ChatCluster");
        eventLoop();
        channel.close();
    }

    private void eventLoop() {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.println("Digite 'cadastrar' para cadastrar um cliente ou 'sair' para encerrar:");
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

                if (line.startsWith("consultar")) {

                    System.out.println("Digite o nome do cliente:");
                    String nome = in.readLine().toLowerCase();

                    //enviarCadastroCliente(nome);
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
}