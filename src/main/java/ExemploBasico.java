import org.jgroups.*;
import org.jgroups.util.*;

public class ExemploBasico {
    JChannel channel; // canal multicast do JGruoups

    private void start() throws Exception {
        System.out.println("JGroups");
    }

    // mensagens informativas, que não demandam uma reposta
    public void receive(Message msg) {

    }

    // alterações na composição do grupo (quando alguém entra ou alguém sai)
    public void viewAccepted(View composicaoDoGrupo) {

    }

    public static void main(String[] args) throws Exception {
        new ExemploBasico().start( );
    }

    private void minhaAplicacao() {


    }

}
