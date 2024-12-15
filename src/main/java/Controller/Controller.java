package Controller;


import Storage.Entities.Conta.Conta;
import org.jgroups.*;
import org.jgroups.blocks.*;
import org.jgroups.util.*;
import org.jgroups.util.Base64;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.*;


public class Controller implements Receiver, RequestHandler {
    private JChannel channel;
    private MessageDispatcher despachante;
    private HashMap<Integer, Conta> contas = new HashMap<>();
    final List<String> state=new LinkedList<String>();
    private int idConta = 1;  // Contador para gerar IDs únicos de conta
    private Map<String, Conta> clientes = new HashMap<>();  // Mapa para armazenar clientes

    public static void main(String[] args) {
        try {
            new Controller().start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void start() throws Exception {
        channel=new JChannel().setReceiver(this);
        channel.connect("ChatCluster");
        channel.getState(null, 10000);
        eventLoop();
        channel.close();

    }

    private void eventLoop() throws Exception {
        System.out.println("[DEBUG] Controller ativo. Digite 'sair' para encerrar.");
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                Thread.sleep(1000);
                String line = scanner.nextLine().toLowerCase();
                if ("sair".equals(line)) {
                    break;
                }
            }
        }
    }

    public void receive(Message msg) {
        try {
            Object object = msg.getObject();  // Objeto recebido (Conta)

            if (object instanceof Conta) {
                // Desserializando o objeto Conta enviado pelo cliente
                Conta conta = (Conta) object;
                System.out.println("[SERVIDOR] Conta recebida: " + conta + ", CPF: " + conta.getNumeroConta());

                // Armazenando a conta no mapa de clientes
                clientes.put(conta.getNumeroConta(), conta);  // Associando a conta pelo número da conta ou outro identificador


            } else if (object instanceof String) {
                String line = msg.getSrc() + ": " + (String) object;
                System.out.println(line);
                synchronized(state) {
                    state.add(line);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getState(Base64.OutputStream output) throws Exception {
        synchronized(state) {
            Util.objectToStream(state, new DataOutputStream(output));
        }
    }

    public void setState(Base64.InputStream input) throws Exception {
        List<String> list;
        list=(List<String>)Util.objectFromStream(new DataInputStream(input));
        synchronized(state) {
            state.clear();
            state.addAll(list);
        }
        System.out.println(list.size() + " messages in chat history):");
        list.forEach(System.out::println);
    }

    public void viewAccepted(View new_view) { //exibe alterações na composição do cluster
        System.err.println("\t\t\t\t\t[DEBUG] ** view: " + new_view);
    }

    class Pedido{
        final static int TIPO_SALDO = 0;
        final static int TIPO_TRANSFERENCIA = 1;

        int tipoPedido;
        int numConta;

        float valor;
        int contaDestino;

    }

    @Override
    public Object handle(Message msg) throws Exception {
        Object object = msg.getObject();  // Obtém o objeto enviado pela mensagem

        if (object instanceof Pedido) {
            Pedido pedido = (Pedido) object;  // Converte o objeto para Pedido

            switch (pedido.tipoPedido) {
                case Pedido.TIPO_SALDO:
                    // Recupera a conta do mapa 'contas' usando o número da conta
                    Conta conta = contas.get(pedido.numConta);
                    if (conta == null) {
                        return "Conta não encontrada.";
                    }
                    // Retorna o saldo da conta
                    return conta.getSaldo();

                case Pedido.TIPO_TRANSFERENCIA:
                    return "Transferência ainda não implementada.";

                default:
                    return "Pedido inválido.";
            }
        }
        return "Mensagem inválida.";  // Se o objeto não for do tipo Pedido
    }

    public Conta consultarConta(String cpf) {
        return clientes.get(cpf);
    }
}
