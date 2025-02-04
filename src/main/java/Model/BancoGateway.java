package Model;

import Storage.Entities.Conta.Conta;
import org.jgroups.*;
import org.jgroups.blocks.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class BancoGateway extends UnicastRemoteObject implements BancoGatewayInterface {
    private JChannel channel;
    private Map<String, Conta> clientes = new HashMap<>();
    private String caminhoJson = retornaDiretorio("clientes.json");

    public BancoGateway() throws RemoteException {
        try {
            // Conectar ao canal JGroups
            channel = new JChannel(retornaDiretorio("cast.xml"));
            channel.setReceiver(new Receiver() {
                @Override
                public void receive(Message msg) {
                    // Lógica para receber mensagens do cluster
                    System.out.println("[GATEWAY] Mensagem recebida do cluster: " + msg.getObject());
                }

                @Override
                public void viewAccepted(View new_view) {
                    System.out.println("[GATEWAY] Nova visão do cluster: " + new_view);
                }
            });
            channel.connect("BancoCluster");
            System.out.println("[GATEWAY] Conectado ao cluster JGroups.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String retornaDiretorio(String document) {
        // Obtém o diretório atual onde o programa está rodando
        String dirPath = new File("").getAbsolutePath();

        return dirPath + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + document;
    }

    @Override
    public boolean realizarLogin(String nome, String senha) throws RemoteException {
        // Verifica as credenciais no arquivo JSON
        return verificarCredenciais(nome, senha);
    }

    @Override
    public boolean cadastrarCliente(String nome, String senha) throws RemoteException {
        // Verifica se o cliente já existe
        if (clienteExistente(nome)) {
            return false;
        }

        // Cria uma nova conta e a adiciona ao mapa de clientes
        Conta conta = new Conta(nome, senha);
        clientes.put(nome, conta);
        salvarCadastroEmArquivo(nome, senha);
        return true;
    }

    @Override
    public String consultarSaldo(String nome) throws RemoteException {
        // Consulta o saldo do cliente no arquivo JSON
        return consultarSaldoClientePorNome(nome);
    }

    @Override
    public BigDecimal somarSaldos() throws RemoteException {
        // Soma os saldos de todos os clientes no arquivo JSON
        String resultado = somarSaldosClientes();
        return new BigDecimal(resultado.replace("[SERVIDOR] A soma dos saldos é: ", ""));
    }

    @Override
    public boolean removerCliente(String nome) throws RemoteException {
        // Remove o cliente do arquivo JSON
        String resultado = removerClienteDoArquivo(nome);
        return resultado.startsWith("[SERVIDOR] Cliente removido com sucesso.");
    }

    @Override
    public boolean alterarSenha(String nome, String novaSenha) throws RemoteException {
        // Altera a senha do cliente no arquivo JSON
        String resultado = alterarSenhaCliente(nome, novaSenha);
        return resultado.startsWith("[SERVIDOR] Senha do cliente alterada com sucesso.");
    }

    @Override
    public boolean realizarTransferencia(String remetente, String destinatario, BigDecimal valor)
            throws RemoteException {
        // Realiza a transferência entre contas
        int idOrigem = Integer.parseInt(consultarIdClienteOrigem(remetente));
        int idDestino = Integer.parseInt(consultarIdClienteDestino(destinatario));

        if (idOrigem == -1 || idDestino == -1) {
            return false; // Conta de origem ou destino não encontrada
        }

        BigDecimal saldoOrigem = consultarSaldoClientePorId(idOrigem);
        BigDecimal saldoDestino = consultarSaldoClientePorId(idDestino);

        if (saldoOrigem.compareTo(valor) < 0) {
            return false; // Saldo insuficiente
        }

        // Realiza a transferência
        Conta contaOrigem = new Conta(idOrigem, remetente, saldoOrigem.subtract(valor));
        Conta contaDestino = new Conta(idDestino, destinatario, saldoDestino.add(valor));

        atualizarSaldoNoArquivo(contaOrigem);
        atualizarSaldoNoArquivo(contaDestino);

        return true;
    }

    private void atualizarSaldoNoArquivo(Conta conta) {
        File arquivo = new File(caminhoJson);

        if (!arquivo.exists() || arquivo.length() == 0) {
            System.out.println("[SERVIDOR] Arquivo JSON vazio ou não encontrado.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            StringBuilder sb = new StringBuilder();
            String linha;
            while ((linha = reader.readLine()) != null) {
                sb.append(linha);
            }

            // Converte o conteúdo do arquivo em um JSONArray
            String content = sb.toString().trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                JSONArray clientesArray = new JSONArray(content);

                // Localiza a conta e atualiza o saldo
                for (int i = 0; i < clientesArray.length(); i++) {
                    JSONObject cliente = clientesArray.getJSONObject(i);
                    if (cliente.getInt("id") == conta.getId()) {
                        cliente.put("saldo", conta.getSaldo());
                        break;
                    }
                }

                // Escreve o array atualizado de volta no arquivo
                try (FileWriter file = new FileWriter(arquivo)) {
                    file.write(clientesArray.toString(4));
                    file.flush();
                }

                System.out.println("[SERVIDOR] Saldo atualizado no arquivo JSON.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String consultarIdClienteOrigem(String nome) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Converte o conteúdo do arquivo em um array JSON
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);

                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equalsIgnoreCase(nome)) {
                            // Se o cliente for encontrado, retorne o ID da conta no formato esperado
                            int idConta = cliente.getInt("id"); // Supondo que o ID está no campo "id"
                            return "[SERVIDOR] Conta encontrada: origem=" + idConta; // Retorna a mensagem com o ID da
                                                                                     // conta
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "[SERVIDOR] Cliente não encontrado."; // Caso o cliente não seja encontrado
    }

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
            int idConta = maiorId + 1;

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

    private BigDecimal consultarSaldoClientePorId(int idConta) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Converte o conteúdo do arquivo em um array JSON
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);

                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getInt("id") == idConta) {
                            // Retorna o saldo como BigDecimal
                            return new BigDecimal(cliente.getDouble("saldo"));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Retorna BigDecimal.ZERO caso o cliente não seja encontrado
        return BigDecimal.ZERO;
    }

    private String consultarSaldoClientePorNome(String nome) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Converte o conteúdo do arquivo em um array JSON
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);

                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equalsIgnoreCase(nome)) {
                            // Retorna apenas o saldo do cliente
                            return String.valueOf(cliente.getDouble("saldo")); // Supondo que saldo é um campo numérico
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "[SERVIDOR] Cliente não encontrado.";
    }


    private String consultarIdClienteDestino(String nome) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Converte o conteúdo do arquivo em um array JSON
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);

                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equalsIgnoreCase(nome)) {
                            // Se o cliente for encontrado, retorne o ID da conta no formato esperado
                            int idConta = cliente.getInt("id"); // Supondo que o ID está no campo "id"
                            return "[SERVIDOR] Conta encontrada: destino=" + idConta; // Retorna a mensagem com o ID da
                                                                                      // conta
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "[SERVIDOR] Cliente não encontrado."; // Caso o cliente não seja encontrado
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

    private boolean verificarCredenciais(String nome, String senha) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Converte o conteúdo do arquivo em um JSONArray
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);

                    // Verifica o nome e a senha
                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equals(nome) && cliente.getString("senha").equals(senha)) {
                            return true; // Credenciais válidas
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false; // Credenciais inválidas
    }
}
