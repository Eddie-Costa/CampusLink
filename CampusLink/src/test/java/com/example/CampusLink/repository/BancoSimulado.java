// banco de dados simulado para os testes devolve linhas falsas e registra os comandos sql enviados pelos repositorios
package com.example.CampusLink.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

// simulacao do banco esta classe responde consultas atualizacoes e transacoes sem abrir conexao real
public class BancoSimulado {

    public static final LocalDateTime DATA_PADRAO = LocalDateTime.of(2026, 11, 10, 10, 0);

    private final Map<String, List<Map<String, Object>>> consultas = new LinkedHashMap<>();
    private final Map<String, Integer> alteracoes = new LinkedHashMap<>();
    private final List<String> falhas = new ArrayList<>();
    private final List<Comando> comandos = new ArrayList<>();
    private int linhasPadrao = 1;
    private int alteradasPadrao = 1;
    private long chaveGerada = 1L;
    private int commits;
    private int rollbacks;

    private final DataSource dataSource = criar(DataSource.class, this::responderDataSource);

    // comando enviado ao banco este registro guarda o sql e os parametros na ordem informada
    public record Comando(String sql, Map<Integer, Object> parametros) {

        // listar parametros do comando este metodo devolve os valores pela posicao do sql
        public List<Object> valores() {
            return new ArrayList<>(new TreeMap<>(parametros).values());
        }
    }

    // montar linha do resultado este metodo recebe pares de coluna e valor
    public static Map<String, Object> linha(Object... colunaValor) {
        Map<String, Object> linha = new LinkedHashMap<>();
        // montar pares da linha guarda cada coluna com o valor seguinte
        for (int i = 0; i < colunaValor.length; i += 2) {
            linha.put(((String) colunaValor[i]).toLowerCase(), colunaValor[i + 1]);
        }
        return linha;
    }

    // configurar consulta este metodo define as linhas devolvidas para o sql que contem o trecho
    @SafeVarargs
    public final BancoSimulado quandoConsultar(String trecho, Map<String, Object>... linhas) {
        consultas.put(trecho, List.of(linhas));
        return this;
    }

    // configurar consulta vazia este metodo faz o sql que contem o trecho voltar sem linhas
    public BancoSimulado semResultado(String trecho) {
        consultas.put(trecho, List.of());
        return this;
    }

    // configurar alteracao este metodo define quantas linhas o comando que contem o trecho altera
    public BancoSimulado quandoAlterar(String trecho, int linhasAlteradas) {
        alteracoes.put(trecho, linhasAlteradas);
        return this;
    }

    // configurar falha este metodo faz o banco lancar erro ao executar o sql que contem o trecho
    public BancoSimulado falharAo(String trecho) {
        falhas.add(trecho);
        return this;
    }

    // configurar padrao das consultas este metodo define quantas linhas voltam quando nao ha regra
    public BancoSimulado linhasPadrao(int linhas) {
        this.linhasPadrao = linhas;
        return this;
    }

    // configurar padrao das alteracoes este metodo define quantas linhas um comando altera quando nao ha regra
    public BancoSimulado alteradasPadrao(int linhas) {
        this.alteradasPadrao = linhas;
        return this;
    }

    // configurar chave gerada este metodo define o codigo devolvido apos um insert
    public BancoSimulado chaveGerada(long chave) {
        this.chaveGerada = chave;
        return this;
    }

    // entregar conexoes simuladas este metodo devolve o datasource usado pelos repositorios
    public DataSource dataSource() {
        return dataSource;
    }

    // entregar jdbc real sobre o banco simulado este metodo devolve um jdbctemplate ligado ao datasource falso
    public JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    // entregar transacoes simuladas este metodo devolve o gerenciador de transacoes do datasource falso
    public DataSourceTransactionManager transacoes() {
        return new DataSourceTransactionManager(dataSource);
    }

    // contar confirmacoes este metodo informa quantas transacoes foram confirmadas
    public int commits() {
        return commits;
    }

    // contar desfazimentos este metodo informa quantas transacoes foram desfeitas
    public int rollbacks() {
        return rollbacks;
    }

    // listar comandos enviados este metodo devolve tudo que os repositorios mandaram executar
    public List<Comando> comandos() {
        return comandos;
    }

    // conferir comando enviado este metodo informa se algum sql com o trecho foi executado
    public boolean executou(String trecho) {
        return comandos.stream().anyMatch(comando -> comando.sql().contains(trecho));
    }

    // buscar ultimo comando este metodo devolve o ultimo sql executado que contem o trecho
    public Comando ultimo(String trecho) {
        Comando encontrado = null;
        // procurar comando percorre o historico e guarda o ultimo que combina
        for (Comando comando : comandos) {
            if (comando.sql().contains(trecho)) {
                encontrado = comando;
            }
        }
        // avisar comando ausente informa ao teste que o sql esperado nao foi enviado
        if (encontrado == null) {
            throw new AssertionError("nenhum comando executado contem: " + trecho + "\nexecutados: " + comandos);
        }
        return encontrado;
    }

    // escolher regra mais especifica este metodo procura o maior trecho configurado presente no sql
    private <T> T regra(Map<String, T> regras, String sql) {
        String melhor = null;
        for (String trecho : regras.keySet()) {
            if (sql.contains(trecho) && (melhor == null || trecho.length() > melhor.length())) {
                melhor = trecho;
            }
        }
        return melhor == null ? null : regras.get(melhor);
    }

    // responder datasource este metodo abre uma conexao simulada a cada pedido
    private Object responderDataSource(Object proxy, Method metodo, Object[] args) {
        // abrir conexao entrega uma nova conexao falsa
        if (metodo.getName().equals("getConnection")) {
            return criar(Connection.class, new ConexaoSimulada());
        }
        return padrao(proxy, metodo, args);
    }

    // conexao simulada esta classe cria comandos preparados e arrays do postgres
    private class ConexaoSimulada implements InvocationHandler {
        private boolean autoCommit = true;

        @Override
        public Object invoke(Object proxy, Method metodo, Object[] args) {
            switch (metodo.getName()) {
                case "prepareStatement", "prepareCall":
                    return criar(PreparedStatement.class, new ComandoSimulado((String) args[0], (Connection) proxy));
                case "createStatement":
                    return criar(PreparedStatement.class, new ComandoSimulado("", (Connection) proxy));
                case "createArrayOf":
                    return array(args[1]);
                case "getMetaData":
                    return criar(DatabaseMetaData.class, (p, m, a) -> m.getName().equals("getDatabaseProductName") ? "PostgreSQL" : padrao(p, m, a));
                case "getAutoCommit":
                    return autoCommit;
                case "setAutoCommit":
                    autoCommit = (Boolean) args[0];
                    return null;
                case "commit":
                    commits++;
                    return null;
                case "rollback":
                    rollbacks++;
                    return null;
                default:
                    return padrao(proxy, metodo, args);
            }
        }
    }

    // comando preparado simulado esta classe guarda parametros e devolve resultados conforme as regras
    private class ComandoSimulado implements InvocationHandler {
        private String sql;
        private final Connection conexao;
        private final Map<Integer, Object> parametros = new LinkedHashMap<>();
        private int lotes;

        ComandoSimulado(String sql, Connection conexao) {
            this.sql = sql;
            this.conexao = conexao;
        }

        @Override
        public Object invoke(Object proxy, Method metodo, Object[] args) throws SQLException {
            String nome = metodo.getName();

            // guardar parametro registra o valor informado em cada posicao do sql
            if (nome.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer posicao) {
                parametros.put(posicao, nome.equals("setNull") ? null : args[1]);
                return null;
            }

            switch (nome) {
                case "executeQuery":
                    usarSqlInformado(args);
                    registrar();
                    return resultado(sql);
                case "executeUpdate", "executeLargeUpdate":
                    usarSqlInformado(args);
                    registrar();
                    return nome.equals("executeUpdate") ? (Object) alteradas() : (Object) (long) alteradas();
                case "execute":
                    usarSqlInformado(args);
                    registrar();
                    return sql.trim().toUpperCase().startsWith("SELECT");
                case "getResultSet":
                    return resultado(sql);
                case "getUpdateCount":
                    return -1;
                case "addBatch":
                    registrar();
                    lotes++;
                    parametros.clear();
                    return null;
                case "executeBatch":
                    int[] totais = new int[lotes];
                    java.util.Arrays.fill(totais, alteradas());
                    return totais;
                case "getGeneratedKeys":
                    return criar(ResultSet.class, new ResultadoSimulado(List.of(linha("id", chaveGerada))));
                case "getConnection":
                    return conexao;
                default:
                    return padrao(proxy, metodo, args);
            }
        }

        // trocar sql do comando usa o sql passado direto ao executar quando ele existe
        private void usarSqlInformado(Object[] args) {
            if (args != null && args.length > 0 && args[0] instanceof String texto) {
                sql = texto;
            }
        }

        // registrar comando guarda o sql e uma copia dos parametros e lanca a falha configurada
        private void registrar() throws SQLException {
            comandos.add(new Comando(sql, new LinkedHashMap<>(parametros)));
            // simular falha do banco lanca erro para o sql configurado como falho
            for (String trecho : falhas) {
                if (sql.contains(trecho)) {
                    throw new SQLException("falha simulada no banco: " + trecho);
                }
            }
        }

        // calcular linhas alteradas usa a regra do sql ou o padrao do teste
        private int alteradas() {
            Integer configurado = regra(alteracoes, sql);
            return configurado == null ? alteradasPadrao : configurado;
        }
    }

    // montar resultado da consulta este metodo usa as linhas configuradas ou linhas padrao
    private ResultSet resultado(String sql) {
        List<Map<String, Object>> linhas = regra(consultas, sql);
        // usar linhas padrao cria linhas vazias quando o teste nao configurou a consulta
        if (linhas == null) {
            linhas = new ArrayList<>();
            for (int i = 0; i < linhasPadrao; i++) {
                linhas.add(new LinkedHashMap<>());
            }
        }
        return criar(ResultSet.class, new ResultadoSimulado(linhas));
    }

    // resultado simulado esta classe percorre as linhas e converte cada coluna para o tipo pedido
    private class ResultadoSimulado implements InvocationHandler {
        private final List<Map<String, Object>> linhas;
        private int atual = -1;
        private boolean ultimoNulo;

        ResultadoSimulado(List<Map<String, Object>> linhas) {
            this.linhas = linhas;
        }

        @Override
        public Object invoke(Object proxy, Method metodo, Object[] args) {
            String nome = metodo.getName();
            switch (nome) {
                case "next":
                    atual++;
                    return atual < linhas.size();
                case "wasNull":
                    return ultimoNulo;
                case "getMetaData":
                    return metadados();
                case "getRow":
                    return atual + 1;
                case "findColumn":
                    return 1;
                default:
                    break;
            }

            // ler coluna converte o valor guardado ou um valor padrao para o tipo do metodo
            if (nome.startsWith("get") && args != null && args.length >= 1) {
                Class<?> tipo = args.length == 2 && args[1] instanceof Class<?> pedido ? pedido : metodo.getReturnType();
                Map<String, Object> linha = linhas.get(Math.max(atual, 0));
                Object valor = valorDaColuna(linha, args[0]);
                boolean informado = valor != null || contemColuna(linha, args[0]);
                ultimoNulo = informado && valor == null;
                // devolver nulo respeita colunas configuradas como vazias
                if (ultimoNulo) {
                    return tipo.isPrimitive() ? padraoPrimitivo(tipo) : null;
                }
                return converter(informado ? valor : valorPadrao(tipo, args[0]), tipo);
            }
            return padrao(proxy, metodo, args);
        }

        // descrever colunas este metodo informa nomes e quantidade de colunas para o jdbctemplate
        private ResultSetMetaData metadados() {
            List<String> colunas = linhas.isEmpty() || linhas.get(0).isEmpty()
                    ? List.of("id") : new ArrayList<>(linhas.get(0).keySet());
            return criar(ResultSetMetaData.class, (p, m, a) -> switch (m.getName()) {
                case "getColumnCount" -> colunas.size();
                case "getColumnLabel", "getColumnName" -> colunas.get((Integer) a[0] - 1);
                case "getColumnType" -> Types.OTHER;
                default -> padrao(p, m, a);
            });
        }

        // buscar valor da coluna este metodo aceita nome ou posicao da coluna
        private Object valorDaColuna(Map<String, Object> linha, Object coluna) {
            if (coluna instanceof Integer posicao) {
                List<Object> valores = new ArrayList<>(linha.values());
                return posicao - 1 < valores.size() ? valores.get(posicao - 1) : null;
            }
            return linha.get(String.valueOf(coluna).toLowerCase());
        }

        // conferir coluna configurada este metodo diferencia coluna nula de coluna nao informada
        private boolean contemColuna(Map<String, Object> linha, Object coluna) {
            if (coluna instanceof Integer posicao) {
                return posicao - 1 < linha.size();
            }
            return linha.containsKey(String.valueOf(coluna).toLowerCase());
        }
    }

    // gerar valor padrao este metodo cria um valor valido para cada tipo de coluna nao configurada
    private static Object valorPadrao(Class<?> tipo, Object coluna) {
        if (tipo == String.class) {
            return "1";
        }
        if (tipo == boolean.class || tipo == Boolean.class) {
            return true;
        }
        if (tipo == Array.class) {
            return new Long[]{1L};
        }
        if (tipo == Object.class) {
            return 1L;
        }
        return DATA_PADRAO;
    }

    // converter valor este metodo adapta numeros textos datas e listas para o tipo pedido pelo repositorio
    private static Object converter(Object valor, Class<?> tipo) {
        if (valor == null) {
            return tipo.isPrimitive() ? padraoPrimitivo(tipo) : null;
        }
        if (tipo == Object.class || tipo.isInstance(valor) && !(valor instanceof LocalDateTime)) {
            return valor;
        }
        if (tipo == String.class) {
            return String.valueOf(valor);
        }
        if (tipo == long.class || tipo == Long.class) {
            return valor instanceof Number n ? n.longValue() : valor instanceof LocalDateTime ? 1L : Long.parseLong(valor.toString());
        }
        if (tipo == int.class || tipo == Integer.class) {
            return valor instanceof Number n ? n.intValue() : valor instanceof LocalDateTime ? 1 : Integer.parseInt(valor.toString());
        }
        if (tipo == short.class || tipo == Short.class) {
            return valor instanceof Number n ? n.shortValue() : (short) 1;
        }
        if (tipo == double.class || tipo == Double.class) {
            return valor instanceof Number n ? n.doubleValue() : 1.0;
        }
        if (tipo == float.class || tipo == Float.class) {
            return valor instanceof Number n ? n.floatValue() : 1.0f;
        }
        if (tipo == BigDecimal.class) {
            return valor instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ONE;
        }
        if (tipo == boolean.class || tipo == Boolean.class) {
            return valor instanceof Boolean b ? b : Boolean.parseBoolean(valor.toString());
        }
        if (tipo == UUID.class) {
            return valor instanceof UUID u ? u : UUID.nameUUIDFromBytes(valor.toString().getBytes());
        }
        if (tipo == Array.class) {
            return array(valor);
        }
        LocalDateTime data = valor instanceof LocalDateTime d ? d
                : valor instanceof LocalDate d ? d.atStartOfDay()
                : valor instanceof OffsetDateTime d ? d.toLocalDateTime()
                : valor instanceof Timestamp t ? t.toLocalDateTime()
                : DATA_PADRAO;
        if (tipo == Timestamp.class) {
            return Timestamp.valueOf(data);
        }
        if (tipo == java.sql.Date.class) {
            return java.sql.Date.valueOf(data.toLocalDate());
        }
        if (tipo == java.sql.Time.class) {
            return java.sql.Time.valueOf(data.toLocalTime());
        }
        if (tipo == LocalDateTime.class) {
            return data;
        }
        if (tipo == LocalDate.class) {
            return data.toLocalDate();
        }
        if (tipo == LocalTime.class) {
            return data.toLocalTime();
        }
        if (tipo == OffsetDateTime.class) {
            return data.atOffset(ZoneOffset.of("-03:00"));
        }
        return valor;
    }

    // montar array do postgres este metodo embrulha listas e vetores como java sql array
    private static Array array(Object valor) {
        Object[] itens = valor instanceof Collection<?> lista ? lista.toArray()
                : valor instanceof Object[] vetor ? vetor : new Object[]{valor};
        return criar(Array.class, (p, m, a) -> switch (m.getName()) {
            case "getArray" -> itens;
            case "getBaseTypeName" -> "int8";
            default -> padrao(p, m, a);
        });
    }

    // valor padrao de tipo primitivo este metodo devolve zero ou falso
    private static Object padraoPrimitivo(Class<?> tipo) {
        if (tipo == boolean.class) return false;
        if (tipo == long.class) return 0L;
        if (tipo == int.class) return 0;
        if (tipo == double.class) return 0.0;
        if (tipo == float.class) return 0f;
        if (tipo == short.class) return (short) 0;
        if (tipo == byte.class) return (byte) 0;
        if (tipo == char.class) return '\0';
        return null;
    }

    // resposta padrao dos objetos simulados este metodo trata metodos de object e devolve vazio no resto
    private static Object padrao(Object proxy, Method metodo, Object[] args) {
        return switch (metodo.getName()) {
            case "toString" -> "simulado:" + metodo.getDeclaringClass().getSimpleName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "isWrapperFor" -> false;
            default -> padraoPrimitivo(metodo.getReturnType());
        };
    }

    // criar objeto simulado este metodo gera uma implementacao da interface jdbc pedida
    @SuppressWarnings("unchecked")
    private static <T> T criar(Class<T> tipo, InvocationHandler resposta) {
        return (T) Proxy.newProxyInstance(BancoSimulado.class.getClassLoader(), new Class<?>[]{tipo}, resposta);
    }
}
