import Storage.Entities.Conta.Conta;
import org.jgroups.*;
import org.jgroups.protocols.UDP;
import org.jgroups.stack.DiagnosticsHandler;
import org.jgroups.stack.ProtocolStack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

public class Cliente {
    private JChannel channel;
    private int idConta = 1;

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

                    System.out.println("Digite o CPF do cliente:");
                    String cpf = in.readLine().toLowerCase();

                    System.out.println("Digite a senha do cliente:");
                    String senha = in.readLine().toLowerCase();

                    enviarCadastroCliente(nome, cpf, senha);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void enviarCadastroCliente(String nome, String cpf, String senha) {
        try {
            Conta conta = new Conta(idConta, nome, senha, cpf);
            ObjectMessage msg = new ObjectMessage(null, conta);

            String mensagem = "Cadastrar Cliente: Nome=" + nome + ", CPF=" + cpf + ", Senha=" + senha;
            System.out.println("[CLIENTE] Dados enviados: " + mensagem);

            // Enviar os dados para o servidor
            channel.send(msg);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}