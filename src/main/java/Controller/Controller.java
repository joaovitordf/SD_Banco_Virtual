package Controller;


import Storage.Entities.Conta.Conta;
import org.jgroups.*;
import org.jgroups.blocks.*;
import org.jgroups.util.*;
import org.jgroups.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.*;


public class Controller implements Receiver, RequestHandler {
    private JChannel channel;
    private MessageDispatcher despachante;
    private HashMap<Integer, Conta> contas = new HashMap<>();
    final List<String> state=new LinkedList<String>();
    private int idConta = 1;  // Contador para gerar IDs únicos de conta
    private Map<String, Conta> clientes = new HashMap<>();  // Mapa para armazenar clientes
    private String caminhoJson = "C:/Users/xjoao/IdeaProjects/SD_Banco_Virtual/src/main/java/clientes.json";

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
                System.out.println("[SERVIDOR] Conta recebida: " + conta + ", Nome: " + conta.getNome());

                // Armazenando a conta no mapa de clientes
                clientes.put(conta.getNome(), conta);  // Associando a conta pelo número da conta ou outro identificador

                salvarCadastroEmArquivo(conta.getNome(), conta.getSenha());
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

    private void salvarCadastroEmArquivo(String nome, String senha) {
        try {
            // Verifica se o nome já existe no json
            if (clienteExistente(nome)) {
                System.out.println("[SERVIDOR] Cliente com o nome " + nome + " já cadastrado.");
                return; // Não permite cadastrar um novo cliente com o mesmo nome
            }

            // Caminho do arquivo JSON
            File arquivo = new File(caminhoJson);

            JSONArray clientesArray = new JSONArray();
            int maiorId = 0;

            if (arquivo.exists() && arquivo.length() > 0) {  // Verifica se o arquivo existe e não está vazio
                // Leitura do conteudo atual do arquivo
                try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                    StringBuilder sb = new StringBuilder();
                    String linha;
                    while ((linha = reader.readLine()) != null) {
                        sb.append(linha);
                    }

                    // Tenta converter o conteúdo do arquivo em um array JSON
                    String content = sb.toString().trim();
                    if (content.startsWith("[") && content.endsWith("]")) {
                        // Se o conteúdo for um array válido, converte
                        clientesArray = new JSONArray(content);
                        // Determina o maior id no JSON
                        for (int i = 0; i < clientesArray.length(); i++) {
                            JSONObject cliente = clientesArray.getJSONObject(i);
                            if (cliente.has("id")) {
                                maiorId = Math.max(maiorId, cliente.getInt("id"));
                            }
                        }
                    } else {
                        // Se não for um array válido, cria um novo array vazio
                        clientesArray = new JSONArray();
                    }
                }
            }

            // Incrementa o ID para o próximo cliente
            idConta = maiorId + 1;

            // Cria um objeto JSON com os dados do cliente
            JSONObject json = new JSONObject();
            json.put("id", idConta);
            json.put("nome", nome);
            json.put("senha", senha);
            json.put("saldo", 1000);

            // Adiciona o novo cliente ao array
            clientesArray.put(json);

            // Escreve o array JSON de volta no arquivo
            try (FileWriter file = new FileWriter(arquivo)) {
                file.write(clientesArray.toString(4)); // '4' adiciona identação para melhorar a leitura
                file.flush();
            }

            System.out.println("[SERVIDOR] Cadastro de cliente armazenado no arquivo.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Verifica se o cliente já existe pelo nome
    private boolean clienteExistente(String nome) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Tenta converter o conteúdo do arquivo em um array JSON
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);
                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equals(nome)) {
                            return true; // Cliente já existe
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false; // Cliente não encontrado
    }

    public Conta consultarConta(String cpf) {
        return clientes.get(cpf);
    }

}
