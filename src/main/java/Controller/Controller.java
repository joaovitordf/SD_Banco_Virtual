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
    final List<String> state = new LinkedList<String>();
    private int idConta = 1;
    private Map<String, Conta> clientes = new HashMap<>(); // Mapa para armazenar clientes
    private String caminhoJson = "D:/VsCode/SD_Banco_Virtual/src/main/java/clientes.json";

    public static void main(String[] args) {
        try {
            new Controller().start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void start() throws Exception {
        channel = new JChannel().setReceiver(this);
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
            Object object = msg.getObject(); // Objeto recebido (Conta)

            if (object instanceof Conta) {
                // Desserializando o objeto Conta enviado pelo cliente
                Conta conta = (Conta) object;
                System.out.println("[SERVIDOR] Conta recebida: " + conta + ", Nome: " + conta.getNome());

                // Armazenando a conta no mapa de clientes
                clientes.put(conta.getNome(), conta); // Associando a conta pelo numero da conta ou outro identificador

                salvarCadastroEmArquivo(conta.getNome(), conta.getSenha());
            } else if (object instanceof String) {
                String mensagem = (String) object;

                if (mensagem.startsWith("CONSULTAR:")) {
                    // Extrai o nome do cliente a ser consultado
                    String nomeCliente = mensagem.substring(10).trim();

                    // Consulta os dados do cliente
                    String respostaConsulta = consultarCliente(nomeCliente);

                    // Envia a resposta de volta para o cliente
                    Message resposta = new ObjectMessage(msg.getSrc(), respostaConsulta);
                    channel.send(resposta);
                }

                if (((String) object).startsWith("SOMAR_SALDOS")) {
                    System.out.println("[SERVIDOR] Solicitação de soma dos saldos recebida.");
                    String resultado = somarSaldosClientes();
                    try {
                        // Enviar o resultado de volta ao cliente
                        Message resposta = new ObjectMessage(msg.getSrc(), resultado);
                        channel.send(resposta);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (((String) object).startsWith("REMOVER:")) {
                    String nomeCliente = ((String) object).substring(8).trim(); // Extrai o nome após 'REMOVER:'
                    System.out.println("[SERVIDOR] Solicitação de remoção para o cliente: " + nomeCliente);

                    String resultado = removerClienteDoArquivo(nomeCliente); // Remove o cliente do JSON

                    try {
                        // Enviar a resposta ao cliente
                        Message resposta = new ObjectMessage(msg.getSrc(), resultado);
                        channel.send(resposta);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (((String) object).startsWith("ALTERAR:")) {
                    String[] partes = ((String) object).split(":");
                    if (partes.length == 3) {
                        String nomeCliente = partes[1].trim();
                        String novaSenha = partes[2].trim();

                        System.out.println("[SERVIDOR] Solicitação de alteração para o cliente: " + nomeCliente);

                        String resultado = alterarSenhaCliente(nomeCliente, novaSenha); // Altera a senha no JSON

                        try {
                            // Enviar a resposta ao cliente
                            Message resposta = new ObjectMessage(msg.getSrc(), resultado);
                            channel.send(resposta);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("[SERVIDOR] Mensagem de alteração mal formatada.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getState(Base64.OutputStream output) throws Exception {
        synchronized (state) {
            Util.objectToStream(state, new DataOutputStream(output));
        }
    }

    public void setState(Base64.InputStream input) throws Exception {
        List<String> list;
        list = (List<String>) Util.objectFromStream(new DataInputStream(input));
        synchronized (state) {
            state.clear();
            state.addAll(list);
        }
        System.out.println(list.size() + " messages in chat history):");
        list.forEach(System.out::println);
    }

    public void viewAccepted(View new_view) { // exibe alterações na composição do cluster
        System.err.println("\t\t\t\t\t[DEBUG] ** view: " + new_view);
    }

    class Pedido {
        final static int TIPO_SALDO = 0;
        final static int TIPO_TRANSFERENCIA = 1;

        int tipoPedido;
        int numConta;

        float valor;
        int contaOrigem;
        int contaDestino;

    }

    @Override
    public Object handle(Message msg) throws Exception {
        Object object = msg.getObject(); // Obtém o objeto enviado pela mensagem

        if (object instanceof Pedido) {
            Pedido pedido = (Pedido) object; // Converte o objeto para Pedido

            switch (pedido.tipoPedido) {
                case Pedido.TIPO_SALDO:
                    // Recupera a conta do mapa 'contas' usando o numero da conta
                    Conta conta = contas.get(pedido.numConta);
                    if (conta == null) {
                        return "Conta não encontrada.";
                    }
                    // Retorna o saldo da conta
                    return conta.getSaldo();
                /*
                 * case Pedido.TIPO_TRANSFERENCIA:
                 * // Obtém as contas de origem e destino
                 * Conta contaOrigem = contas.get(pedido.numConta);
                 * Conta contaDestino = contas.get(pedido.contaDestino);
                 * 
                 * if (contaOrigem == null || contaDestino == null) {
                 * return "[SERVIDOR] Conta de origem ou destino não encontrada.";
                 * }
                 * 
                 * // Verifica se o valor é positivo
                 * if (pedido.valor <= 0) {
                 * return "[SERVIDOR] O valor da transferência deve ser positivo.";
                 * }
                 * 
                 * // Verifica se há saldo suficiente
                 * if (contaOrigem.getSaldo() < pedido.valor) {
                 * return "[SERVIDOR] Saldo insuficiente na conta de origem.";
                 * }
                 * 
                 * // Realiza a transferência
                 * contaOrigem.setSaldo(contaOrigem.getSaldo() - pedido.valor);
                 * contaDestino.setSaldo(contaDestino.getSaldo() + pedido.valor);
                 * 
                 * // Atualiza o JSON com os novos saldos
                 * atualizarSaldoNoArquivo(contaOrigem);
                 * atualizarSaldoNoArquivo(contaDestino);
                 * 
                 * return "[SERVIDOR] Transferência concluída com sucesso.";
                 */
                default:
                    return "Pedido inválido.";
            }
        }
        return "Mensagem inválida."; // Se o objeto não for do tipo Pedido
    }

    /*
     * private void atualizarSaldoNoArquivo(Conta conta) {
     * File arquivo = new File(caminhoJson);
     * 
     * if (!arquivo.exists() || arquivo.length() == 0) {
     * System.out.println("[SERVIDOR] Arquivo JSON vazio ou não encontrado.");
     * return;
     * }
     * 
     * try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
     * StringBuilder sb = new StringBuilder();
     * String linha;
     * while ((linha = reader.readLine()) != null) {
     * sb.append(linha);
     * }
     * 
     * // Converte o conteúdo do arquivo em um JSONArray
     * String content = sb.toString().trim();
     * if (content.startsWith("[") && content.endsWith("]")) {
     * JSONArray clientesArray = new JSONArray(content);
     * 
     * // Localiza a conta e atualiza o saldo
     * for (int i = 0; i < clientesArray.length(); i++) {
     * JSONObject cliente = clientesArray.getJSONObject(i);
     * if (cliente.getInt("id") == conta.getId()) {
     * cliente.put("saldo", conta.getSaldo());
     * break;
     * }
     * }
     * 
     * // Escreve o array atualizado de volta no arquivo
     * try (FileWriter file = new FileWriter(arquivo)) {
     * file.write(clientesArray.toString(4));
     * file.flush();
     * }
     * 
     * System.out.println("[SERVIDOR] Saldo atualizado no arquivo JSON.");
     * }
     * } catch (Exception e) {
     * e.printStackTrace();
     * }
     * }
     */
    private void salvarCadastroEmArquivo(String nome, String senha) {
        try {
            // Verifica se o nome ja existe no json
            if (clienteExistente(nome)) {
                System.out.println("[SERVIDOR] Cliente com o nome " + nome + " já cadastrado.");
                return; // Não permite cadastrar um novo cliente com o mesmo nome
            }

            // Caminho do arquivo JSON
            File arquivo = new File(caminhoJson);

            JSONArray clientesArray = new JSONArray();
            int maiorId = 0;

            if (arquivo.exists() && arquivo.length() > 0) { // Verifica se o arquivo existe e não esta vazio
                // Leitura do conteudo atual do arquivo
                try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                    StringBuilder sb = new StringBuilder();
                    String linha;
                    while ((linha = reader.readLine()) != null) {
                        sb.append(linha);
                    }

                    // Tenta converter o conteudo do arquivo em um array JSON
                    String content = sb.toString().trim();
                    if (content.startsWith("[") && content.endsWith("]")) {
                        // Se o conteudo for um array valido, converte
                        clientesArray = new JSONArray(content);
                        // Determina o maior id no JSON
                        for (int i = 0; i < clientesArray.length(); i++) {
                            JSONObject cliente = clientesArray.getJSONObject(i);
                            if (cliente.has("id")) {
                                maiorId = Math.max(maiorId, cliente.getInt("id"));
                            }
                        }
                    } else {
                        // Se não for um array valido, cria um novo array vazio
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

    // Verifica se o cliente ja existe pelo nome
    private boolean clienteExistente(String nome) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Tenta converter o conteudo do arquivo em um array JSON
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);
                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equals(nome)) {
                            return true; // Cliente ja existe
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false; // Cliente não encontrado
    }

    private String consultarCliente(String nome) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Converte o conteudo do arquivo em um array JSON
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);

                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equalsIgnoreCase(nome)) {
                            return cliente.toString(4); // Retorna os dados formatados
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "[SERVIDOR] Cliente não encontrado.";
    }

    private String somarSaldosClientes() {
        File arquivo = new File(caminhoJson);

        if (!arquivo.exists() || arquivo.length() == 0) {
            return "[SERVIDOR] Arquivo JSON vazio ou não encontrado.";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            StringBuilder sb = new StringBuilder();
            String linha;
            while ((linha = reader.readLine()) != null) {
                sb.append(linha);
            }

            // Converte o conteudo do arquivo em um JSONArray
            String content = sb.toString().trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                JSONArray clientesArray = new JSONArray(content);

                double somaSaldos = 0;
                for (int i = 0; i < clientesArray.length(); i++) {
                    JSONObject cliente = clientesArray.getJSONObject(i);
                    if (cliente.has("saldo")) {
                        somaSaldos += cliente.getDouble("saldo");
                    }
                }

                return "[SERVIDOR] A soma dos saldos é: " + somaSaldos;
            } else {
                return "[SERVIDOR] Formato de arquivo JSON inválido.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "[SERVIDOR] Erro ao processar a soma dos saldos.";
        }
    }

    private String removerClienteDoArquivo(String nome) {
        File arquivo = new File(caminhoJson);

        if (!arquivo.exists() || arquivo.length() == 0) {
            return "[SERVIDOR] Arquivo JSON vazio ou não encontrado.";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            StringBuilder sb = new StringBuilder();
            String linha;
            while ((linha = reader.readLine()) != null) {
                sb.append(linha);
            }

            // Converte o conteudo do arquivo em um JSONArray
            String content = sb.toString().trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                JSONArray clientesArray = new JSONArray(content);

                // Percorre o array e remove o cliente com o nome correspondente
                boolean clienteRemovido = false;
                for (int i = 0; i < clientesArray.length(); i++) {
                    JSONObject cliente = clientesArray.getJSONObject(i);
                    if (cliente.getString("nome").equalsIgnoreCase(nome)) {
                        clientesArray.remove(i); // Remove o cliente
                        clienteRemovido = true;
                        break;
                    }
                }

                if (clienteRemovido) {
                    // Salva o array atualizado de volta no arquivo
                    try (FileWriter file = new FileWriter(arquivo)) {
                        file.write(clientesArray.toString(4)); // '4' para identação
                        file.flush();
                    }
                    return "[SERVIDOR] Cliente " + nome + " removido com sucesso.";
                } else {
                    return "[SERVIDOR] Cliente " + nome + " não encontrado.";
                }
            } else {
                return "[SERVIDOR] Formato de arquivo JSON inválido.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "[SERVIDOR] Erro ao processar a remoção do cliente.";
        }
    }

    private String alterarSenhaCliente(String nome, String novaSenha) {
        File arquivo = new File(caminhoJson);

        if (!arquivo.exists() || arquivo.length() == 0) {
            return "[SERVIDOR] Arquivo JSON vazio ou não encontrado.";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            StringBuilder sb = new StringBuilder();
            String linha;
            while ((linha = reader.readLine()) != null) {
                sb.append(linha);
            }

            // Converte o conteudo do arquivo em um JSONArray
            String content = sb.toString().trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                JSONArray clientesArray = new JSONArray(content);

                // Percorre o array para localizar o cliente e alterar a senha
                boolean clienteAlterado = false;
                for (int i = 0; i < clientesArray.length(); i++) {
                    JSONObject cliente = clientesArray.getJSONObject(i);
                    if (cliente.getString("nome").equalsIgnoreCase(nome)) {
                        cliente.put("senha", novaSenha); // Atualiza a senha
                        clienteAlterado = true;
                        break;
                    }
                }

                if (clienteAlterado) {
                    // Salva o array atualizado de volta no arquivo
                    try (FileWriter file = new FileWriter(arquivo)) {
                        file.write(clientesArray.toString(4));
                        file.flush();
                    }
                    return "[SERVIDOR] Senha do cliente " + nome + " alterada com sucesso.";
                } else {
                    return "[SERVIDOR] Cliente " + nome + " não encontrado.";
                }
            } else {
                return "[SERVIDOR] Formato de arquivo JSON inválido.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "[SERVIDOR] Erro ao processar a alteração da senha.";
        }
    }

}
