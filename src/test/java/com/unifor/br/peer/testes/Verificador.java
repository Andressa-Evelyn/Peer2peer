package com.unifor.br.peer.testes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Runner de testes minimo, no lugar do JUnit.
 *
 * <p>Motivo: o build ficou sem nenhuma dependencia externa (ver comentario no build.gradle),
 * entao a verificacao roda em qualquer maquina, sem baixar nada. Para trocar por JUnit 5 basta
 * declarar a dependencia e transformar cada bloco {@code v.teste("...", () -> {...})} da
 * {@link SuiteDeTestes} em um metodo anotado com {@code @Test} — as asserções tem a mesma forma.
 */
final class Verificador {

    interface Corpo {
        void executar() throws Exception;
    }

    private final List<String> falhas = new ArrayList<>();
    private int total;

    void teste(String nome, Corpo corpo) {
        total++;
        long inicio = System.currentTimeMillis();
        try {
            corpo.executar();
            System.out.printf("  ok    %s  (%d ms)%n", nome, System.currentTimeMillis() - inicio);
        } catch (Throwable e) {
            falhas.add(nome + " -> " + e);
            System.out.printf("  FALHA %s%n        %s%n", nome, e);
        }
    }

    static void confirmar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }

    static void igual(Object esperado, Object obtido, String mensagem) {
        if (esperado == null ? obtido != null : !esperado.equals(obtido)) {
            throw new AssertionError(mensagem + " (esperado: " + esperado + ", obtido: " + obtido + ")");
        }
    }

    /** Espera uma condicao ficar verdadeira. Rede e assincrona: nenhum teste pode assumir ordem imediata. */
    static void esperarAte(BooleanSupplier condicao, String oQueEsperava) throws InterruptedException {
        esperarAte(condicao, 5000, oQueEsperava);
    }

    static void esperarAte(BooleanSupplier condicao, long limiteMs, String oQueEsperava)
            throws InterruptedException {
        long limite = System.currentTimeMillis() + limiteMs;
        while (System.currentTimeMillis() < limite) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("tempo esgotado esperando: " + oQueEsperava);
    }

    /** Deixa a rede assentar antes de afirmar que algo NAO aconteceu. */
    static void assentar() throws InterruptedException {
        Thread.sleep(400);
    }

    boolean resumo() {
        System.out.println();
        if (falhas.isEmpty()) {
            System.out.println(total + " testes, todos passaram.");
            return true;
        }
        System.out.println(total + " testes, " + falhas.size() + " falharam:");
        falhas.forEach(f -> System.out.println("  - " + f));
        return false;
    }
}
