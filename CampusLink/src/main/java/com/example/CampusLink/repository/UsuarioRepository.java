// banco de usuarios centraliza cadastro acesso dados pessoais e historico
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.service.SupabaseStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.slf4j.helpers.MessageFormatter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// persistencia de contas esta classe grava perfis senhas sessoes e exclusoes com seus vinculos
@Repository
public class UsuarioRepository {

    private static final Logger logger = LoggerFactory.getLogger(UsuarioRepository.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SupabaseStorageService storage;

    // consultar dados pessoais ativos este metodo busca o cadastro e o identificador da conta pelo email
    public DadosUsuarioDTO consultarDadosPessoais(String email) throws SQLException {
        String sql = """
                SELECT u.id, u.nome, u.email, u.telefone, u.datanasc, u.perfil, u.created_at,
                       COALESCE(a.rgm, p.matricula, adm.matricula) AS identificador
                FROM public."USUARIOS" u
                LEFT JOIN public."ALUNOS" a ON a.id_usuario = u.id
                LEFT JOIN public."PROFESSORES" p ON p.id_usuario = u.id
                LEFT JOIN public."ADMINISTRADOR" adm ON adm.id_usuario = u.id
                WHERE LOWER(u.email) = ? AND u.status = true
                """;

        // buscar dados pessoais consulta o usuario pelo email normalizado
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, normalizarEmail(email));

            // ler dados pessoais abre o resultado da consulta do usuario
            try (ResultSet rs = stmt.executeQuery()) {
                // retornar dados pessoais monta os dados do usuario encontrado
                if (rs.next()) {
                    DadosUsuarioDTO dadosUsuario = new DadosUsuarioDTO();
                    dadosUsuario.setId(rs.getLong("id"));
                    dadosUsuario.setNome(rs.getString("nome"));
                    dadosUsuario.setEmail(rs.getString("email"));
                    dadosUsuario.setTelefone(rs.getString("telefone"));
                    dadosUsuario.setDataNascimento(rs.getDate("datanasc").toLocalDate());
                    dadosUsuario.setPerfil(rs.getString("perfil"));
                    dadosUsuario.setIdentificador(rs.getString("identificador"));
                    dadosUsuario.setDataCadastro(rs.getDate("created_at").toLocalDate());

                    return dadosUsuario;
                }

                return null;
            }
        }
    }

    // verificar impedimentos para delete conta este metodo abre a consulta dos vinculos do usuario
    public boolean possuiVinculosParaExclusao(long idUsuario) throws SQLException {
        // conferir vinculos da conta abre a conexao para consultar impedimentos de exclusao
        try (Connection conn = dataSource.getConnection()) {
            return possuiVinculosParaExclusao(conn, idUsuario);
        }
    }

    // consultar impedimentos de exclusao este metodo verifica turmas materiais e eventos sob responsabilidade do professor
    private boolean possuiVinculosParaExclusao(Connection conn, long idUsuario) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM public."PROFESSORES" p WHERE p.id_usuario = ? AND (
                        EXISTS (SELECT 1 FROM public."TURMAS" t WHERE t.id_proprietario = p.id)
                        OR EXISTS (SELECT 1 FROM public."CONTEUDOS" c WHERE c.id_professor = p.id AND c.status <> 'removido')
                        OR EXISTS (SELECT 1 FROM public."EVENTOS" e WHERE e.id_professor = p.id)
                    )
                )
                """;

        // consultar vinculos da conta prepara a busca pelo codigo do usuario
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, idUsuario);

            // ler vinculos da conta retorna se existem registros que impedem a exclusao
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    // anonimizar dados pessoais este metodo confere a conta e troca os dados que identificam o titular por valores anonimos
    public void anonimizarDadosPessoais(long idUsuario, String email) throws SQLException {
        // anonimizar conta abre uma transacao para que todas as trocas acontecam juntas
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // delete dados pessoais confere os impedimentos e tenta excluir a conta com seus registros
            try {

                // proteger administradores bloqueia alteracoes simultaneas na tabela durante a exclusao
                try (PreparedStatement stmt = conn.prepareStatement(
                        "LOCK TABLE public.\"ADMINISTRADOR\" IN SHARE ROW EXCLUSIVE MODE")) {

                    stmt.execute();
                }

                // buscar conta para delete confere codigo e email e bloqueia o registro durante a operacao
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT id FROM public.\"USUARIOS\" WHERE id = ? AND LOWER(email) = ? FOR UPDATE")) {

                    stmt.setLong(1, idUsuario);
                    stmt.setString(2, normalizarEmail(email));

                    // ler conta para delete abre o resultado da consulta da conta
                    try (ResultSet rs = stmt.executeQuery()) {
                        // impedir delete de conta inexistente avisa quando codigo e email nao identificam uma conta
                        if (!rs.next()) {
                            throw new IllegalStateException("Conta não encontrada.");
                        }
                    }
                }

                impedirExclusaoProprioAdministrador(conn, idUsuario);

                // apaga os materiais que ja estavam removidos e seus arquivos
                excluirMateriaisRemovidos(conn, idUsuario);

                // historico academico, chamados e aceites continuam ligados a conta, agora sem identificacao
                executarExclusao(conn, "DELETE FROM public.\"TURMA_ATIVIDADE\" WHERE id_usuario = ?", idUsuario);
                executarExclusao(conn, """
                        UPDATE public."CONSENTIMENTOS" SET revogado_em = now()
                        WHERE id_usuario = ? AND revogado_em IS NULL
                        """, idUsuario);

                anonimizarLogs(conn, normalizarEmail(email));

                executarExclusao(conn, "UPDATE public.\"ALUNOS\" SET rgm = 'ANON-' || id WHERE id_usuario = ?", idUsuario);
                executarExclusao(conn, "UPDATE public.\"PROFESSORES\" SET matricula = 'ANON-' || id WHERE id_usuario = ?", idUsuario);
                executarExclusao(conn, """
                        UPDATE public."USUARIOS"
                        SET nome = 'Usuário removido', email = NULL, telefone = NULL, datanasc = DATE '1900-01-01',
                            senha = '!anonimizado', status = false, anonimizado_em = now()
                        WHERE id = ?
                        """, idUsuario);

                conn.commit();

            } catch (SQLException | RuntimeException e) {
                // desfazer delete da conta tenta cancelar as alteracoes quando a exclusao falha
                try {
                    conn.rollback();

                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }

                throw e;
            }
        }
    }

    // impedir delete da propria conta administrativa este metodo exige que outro administrador realize a exclusao
    private void impedirExclusaoProprioAdministrador(Connection conn, long idUsuario) throws SQLException {
        String sql = "SELECT master FROM public.\"ADMINISTRADOR\" WHERE id_usuario = ?";

        // conferir delete de administrador consulta o perfil administrativo da conta
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, idUsuario);

            // ler perfil administrativo abre o resultado da consulta de administrador
            try (ResultSet rs = stmt.executeQuery()) {
                // impedir delete da propria conta administrativa exige outro administrador para realizar a exclusao
                if (rs.next()) {
                    // proteger administrador master exige outro administrador master para excluir a conta
                    if (rs.getBoolean("master")) {
                        throw new IllegalStateException("Sua conta de administrador master só pode ser excluída por outro administrador master.");
                    }

                    throw new IllegalStateException("Sua conta de administrador deve ser excluída por outro administrador na página de gerenciamento de usuários.");
                }
            }
        }
    }

    // executar limpeza de dados pessoais este metodo preenche os identificadores e executa a alteracao recebida
    private void executarExclusao(Connection conn, String sql, long... ids) throws SQLException {
        // executar delete no banco preenche os codigos e executa a consulta de exclusao
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) {
                stmt.setLong(i + 1, ids[i]);
            }

            stmt.executeUpdate();
        }
    }

    // anonimizar registros este metodo troca o email do titular por [anonimizado] nos logs de eventos e de sessoes
    private void anonimizarLogs(Connection conn, String email) throws SQLException {
        // ***= faz o PostgreSQL tratar o e-mail como texto literal, sem interpretar "." ou "+" como expressao
        String padrao = "***=" + email;
        String contem = "%" + email + "%";

        try (PreparedStatement stmt = conn.prepareStatement("""
                UPDATE public."LOGS_EVENTOS"
                SET mensagem = regexp_replace(mensagem, ?, '[anonimizado]', 'gi'),
                    excecao = regexp_replace(excecao, ?, '[anonimizado]', 'gi'),
                    detalhes = regexp_replace(detalhes::text, ?, '[anonimizado]', 'gi')::jsonb
                WHERE mensagem ILIKE ? OR excecao ILIKE ? OR detalhes::text ILIKE ?
                """)) {
            stmt.setString(1, padrao);
            stmt.setString(2, padrao);
            stmt.setString(3, padrao);
            stmt.setString(4, contem);
            stmt.setString(5, contem);
            stmt.setString(6, contem);
            stmt.executeUpdate();
        }

        try (PreparedStatement stmt = conn.prepareStatement("""
                UPDATE public."LOGS_SESSOES"
                SET log_completo = regexp_replace(log_completo, ?, '[anonimizado]', 'gi')
                WHERE log_completo ILIKE ?
                """)) {
            stmt.setString(1, padrao);
            stmt.setString(2, contem);
            stmt.executeUpdate();
        }
    }

    // delete materiais removidos da conta este metodo apaga anexos armazenados e registros ligados aos materiais do professor
    private void excluirMateriaisRemovidos(Connection conn, long idUsuario) throws SQLException {
        String conteudos = """
                SELECT c.id FROM public."CONTEUDOS" c
                JOIN public."PROFESSORES" p ON p.id = c.id_professor
                WHERE p.id_usuario = ? AND c.status = 'removido' FOR UPDATE OF c
                """;

        // buscar materiais removidos consulta os conteudos do usuario que devem ser apagados
        try (PreparedStatement stmt = conn.prepareStatement(conteudos)) {
            stmt.setLong(1, idUsuario);

            // delete materiais removidos percorre os conteudos e apaga seus arquivos e vinculos
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long idConteudo = rs.getLong("id");

                    // buscar arquivos do material consulta os caminhos dos arquivos do conteudo
                    try (PreparedStatement arquivos = conn.prepareStatement(
                            "SELECT bucket_id, storage_path FROM public.\"ARQUIVOS\" WHERE id_conteudo = ?")) {

                        arquivos.setLong(1, idConteudo);

                        // delete arquivos armazenados percorre os resultados e apaga os arquivos do armazenamento
                        try (ResultSet files = arquivos.executeQuery()) {
                            while (files.next()) {
                                storage.excluir(files.getString("bucket_id"), files.getString("storage_path"));
                            }
                        }
                    }

                    executarExclusao(conn, "DELETE FROM public.\"ARQUIVOS\" WHERE id_conteudo = ?", idConteudo);
                    executarExclusao(conn, "DELETE FROM public.\"EVENTO_CONTEUDO\" WHERE id_conteudo = ?", idConteudo);
                    executarExclusao(conn, "DELETE FROM public.\"DENUNCIAS\" WHERE id_conteudo = ?", idConteudo);
                    executarExclusao(conn, "DELETE FROM public.\"PROGRESSO_CONTEUDO\" WHERE id_conteudo = ?", idConteudo);
                    executarExclusao(conn, "DELETE FROM public.\"CONTEUDOS\" WHERE id = ?", idConteudo);
                }
            }
        }
    }

    // cadastrar usuario no banco este metodo grava a conta ativa e o perfil de aluno ou professor
    public void InsertCadastroUsuarioIntoBD(String Tipo, String IDENTIFICADOR, String NOME, String EMAIL, String TELEFONE, String DATANASC, String SENHA) throws SQLException {
        String emailNormalizado = normalizarEmail(EMAIL);
        String tabelaEspecifica;
        String colunaIdentificador;

        // cadastrar aluno escolhe a tabela de alunos e o identificador rgm
        if (Tipo.equalsIgnoreCase("Aluno")) {
            tabelaEspecifica = "ALUNOS";
            colunaIdentificador = "rgm";

        }
        // cadastrar professor escolhe a tabela de professores e o identificador matricula
        else if (Tipo.equalsIgnoreCase("Professor")) {
            tabelaEspecifica = "PROFESSORES";
            colunaIdentificador = "matricula";

        } else {
            throw new IllegalArgumentException("Tipo de usuário inválido: " + Tipo);
        }

        String sqlUsuario = "INSERT INTO public.\"USUARIOS\" (\"nome\", \"email\", \"telefone\", \"datanasc\", \"senha\", \"perfil\", \"status\") "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        String sqlEspecifico = "INSERT INTO public.\"" + tabelaEspecifica + "\" "
                + "(\"id_usuario\", \"" + colunaIdentificador + "\") VALUES (?, ?)";

        // registrar aceite dos termos grava a versao vigente e seu hash junto com o cadastro
        String sqlAceiteTermos = """
                INSERT INTO public."CONSENTIMENTOS" (id_usuario, tipo, id_termo, hash_sha256)
                SELECT ?, 'termos_uso', id, hash_sha256
                FROM public."TERMOS_VERSOES"
                ORDER BY publicado_em DESC, id DESC
                LIMIT 1
                """;

        // cadastrar usuario abre a transacao e confirma o cadastro do usuario e do perfil
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // gravar cadastro do usuario preenche os dados pessoais e recupera o codigo criado
            try (PreparedStatement stmt = conn.prepareStatement(sqlUsuario, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, NOME);
                stmt.setString(2, emailNormalizado);
                stmt.setString(3, TELEFONE);
                stmt.setDate(4, java.sql.Date.valueOf(java.time.LocalDate.parse(DATANASC)));
                stmt.setString(5, SENHA);
                stmt.setString(6, tabelaEspecifica);
                stmt.setBoolean(7, true);

                // conferir cadastro do usuario avisa quando nenhum usuario foi inserido
                if (stmt.executeUpdate() == 0) {
                    throw new SQLException("Nenhum usuário foi inserido.");
                }

                // ler codigo do novo usuario recupera o codigo para cadastrar o perfil
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    // conferir codigo do novo usuario avisa quando o banco nao retorna o codigo criado
                    if (!generatedKeys.next()) {
                        throw new SQLException("Não foi possível obter o id do usuário inserido.");
                    }

                    long idUsuario = generatedKeys.getLong(1);

                    // cadastrar perfil do usuario grava o identificador junto ao codigo do usuario
                    try (PreparedStatement stmt2 = conn.prepareStatement(sqlEspecifico)) {
                        stmt2.setLong(1, idUsuario);
                        stmt2.setString(2, IDENTIFICADOR);
                        stmt2.executeUpdate();
                    }

                    try (PreparedStatement stmt3 = conn.prepareStatement(sqlAceiteTermos)) {
                        stmt3.setLong(1, idUsuario);
                        if (stmt3.executeUpdate() == 0) {
                            throw new SQLException("Nenhuma versão dos termos publicada para registrar o aceite.");
                        }
                    }
                }
            }

            conn.commit();

        } catch (SQLException e) {
            throw e;
        }
    }

    // registrar evento no historico este metodo grava a ocorrencia e atualiza sessoes ja encerradas
    public void InserirLogsNoBD(UUID sessaoLogId, String nivel, String classe, String operacao,
                                UUID operacaoId, String mensagem, String detalhes, String excecao) throws SQLException {

        // identificar sessao do registro usa a sessao atual quando nenhum codigo foi informado
        if (sessaoLogId == null && MDC.get("sessaoLogId") != null) {
            sessaoLogId = UUID.fromString(MDC.get("sessaoLogId"));
        }

        // gravar registro de atividade abre uma transacao para o evento e o historico da sessao
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // salvar evento da sessao tenta gravar o evento e atualizar o historico antes de confirmar
            try {
                boolean encerrada = sessaoLogId != null && bloquearSessaoLog(conn, sessaoLogId);

                inserirEventoLog(conn, sessaoLogId, nivel, classe, operacao, operacaoId, mensagem, detalhes, excecao);

                // atualizar sessao encerrada refaz o historico quando chega um registro depois do encerramento
                if (encerrada) {
                    atualizarHistoricoSessao(conn, sessaoLogId);
                }

                conn.commit();

            } catch (SQLException | RuntimeException e) {
                desfazerTransacaoLog(conn, e);
                throw e;
            }
        }
    }

    // finalizar registro da sessao este metodo grava o periodo de acesso e consolida o historico
    public void finalizarSessaoLog(UUID sessaoLogId, Instant inicio, Instant fim, String email) throws SQLException {
        String sql = """
                UPDATE public."LOGS_SESSOES"
                SET usuario_id = COALESCE(usuario_id,
                        (SELECT id FROM public."USUARIOS" WHERE LOWER(email) = ?)),
                    inicio = ?, fim = ?, status = 'ENCERRADA'
                WHERE sessao_log_id = ?
                """;

        // finalizar historico da sessao abre uma transacao para registrar o encerramento
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // gravar fim da sessao tenta registrar o encerramento e consolidar seu historico
            try {
                // registrar encerramento da sessao grava o evento de encerramento se a sessao ainda estava aberta
                if (!bloquearSessaoLog(conn, sessaoLogId)) {
                    inserirEventoLog(
                            conn,
                            sessaoLogId,
                            "INFO",
                            "SessaoLogListener",
                            "sessionDestroyed",
                            null,
                            "Sessao invalidada. Historico consolidado.",
                            null,
                            null
                    );

                    // salvar periodo da sessao grava o email e os horarios de inicio e fim
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, email == null ? null : normalizarEmail(email));
                        stmt.setTimestamp(2, Timestamp.from(inicio));
                        stmt.setTimestamp(3, Timestamp.from(fim));
                        stmt.setObject(4, sessaoLogId);
                        stmt.executeUpdate();
                    }
                }

                atualizarHistoricoSessao(conn, sessaoLogId);
                conn.commit();

            } catch (SQLException | RuntimeException e) {
                desfazerTransacaoLog(conn, e);
                throw e;
            }
        }
    }

    // reservar registro da sessao este metodo garante a existencia do registro e informa se ja foi encerrado
    private boolean bloquearSessaoLog(Connection conn, UUID sessaoLogId) throws SQLException {
        String sql = "INSERT INTO public.\"LOGS_SESSOES\" (sessao_log_id) VALUES (?) "
                + "ON CONFLICT (sessao_log_id) DO NOTHING";

        // garantir registro da sessao executa o cadastro usado antes de bloquear a sessao
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessaoLogId);
            stmt.executeUpdate();
        }

        // bloquear sessao para registro consulta o encerramento e bloqueia o registro durante a operacao
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT fim FROM public.\"LOGS_SESSOES\" WHERE sessao_log_id = ? FOR UPDATE")) {

            stmt.setObject(1, sessaoLogId);

            // ler encerramento da sessao verifica se existe um horario de fim
            try (ResultSet rs = stmt.executeQuery()) {
                // conferir sessao do registro avisa quando a sessao nao foi encontrada
                if (!rs.next()) {
                    throw new SQLException("Sessao de log nao encontrada.");
                }

                return rs.getTimestamp("fim") != null;
            }
        }
    }

    // gravar ocorrencia da sessao este metodo salva nivel operacao mensagem detalhes e erro no banco
    private void inserirEventoLog(Connection conn, UUID sessaoLogId, String nivel, String classe, String operacao,
                                  UUID operacaoId, String mensagem, String detalhes, String excecao) throws SQLException {

        String sql = "INSERT INTO public.\"LOGS_EVENTOS\" (sessao_log_id, nivel, classe, operacao, operacao_id, mensagem, detalhes, excecao) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)";

        // cadastrar evento no historico grava os dados da atividade e da excecao informada
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessaoLogId);
            stmt.setString(2, nivel);
            stmt.setString(3, classe);
            stmt.setString(4, operacao);
            stmt.setObject(5, operacaoId);
            stmt.setString(6, mensagem);
            stmt.setString(7, detalhes);
            stmt.setString(8, excecao);

            // conferir evento do historico avisa quando nenhum registro foi inserido
            if (stmt.executeUpdate() == 0) {
                throw new SQLException("Nenhum log foi inserido.");
            }
        }
    }

    // montar historico completo da sessao este metodo junta as ocorrencias em ordem de data
    private void atualizarHistoricoSessao(Connection conn, UUID sessaoLogId) throws SQLException {
        String sql = """
            UPDATE public."LOGS_SESSOES" s SET log_completo = COALESCE(( SELECT string_agg(concat_ws(' ', e.data_hora::text, '[' || e.nivel || ']', e.classe,
            'eventoId=' || e.id, 'operacao=' || e.operacao, 'operacaoId=' || e.operacao_id, e.mensagem, E'\nDetalhes: ' || e.detalhes::text, E'\nExcecao: ' || e.excecao), E'\n' ORDER BY e.data_hora, e.id)
            FROM public."LOGS_EVENTOS" e WHERE e.sessao_log_id = s.sessao_log_id), ''), historico_gerado_em = clock_timestamp() WHERE s.sessao_log_id = ?
            """;

        // consolidar historico da sessao atualiza o historico pelo codigo da sessao
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessaoLogId);
            stmt.executeUpdate();
        }
    }

    // desfazer gravacao do historico este metodo cancela a transacao e preserva uma eventual falha no cancelamento
    private void desfazerTransacaoLog(Connection conn, Exception causa) {
        // desfazer gravacao do historico tenta cancelar a transacao e preserva a falha original
        try {
            conn.rollback();

        } catch (SQLException erroRollback) {
            causa.addSuppressed(erroRollback);
        }
    }

    // validar dados repetidos no cadastro este metodo reune avisos de identificador email e telefone existentes
    public List<String> validarDadosDuplicados(String tipoUsuario, String identificador, String email, String telefone) {
        List<String> erros = new ArrayList<>();

        // validar identificador duplicado acrescenta um erro se o identificador ja esta cadastrado
        if (verificarExistente(tipoUsuario, "identificador", identificador)) {
            erros.add("Identificador já cadastrado");
        }

        // validar email duplicado acrescenta um erro se o email ja esta cadastrado
        if (verificarExistente(tipoUsuario, "email", email)) {
            erros.add("Email já cadastrado");
        }

        // validar telefone duplicado acrescenta um erro se o telefone ja esta cadastrado
        if (verificarExistente(tipoUsuario, "telefone", telefone)) {
            erros.add("Telefone já cadastrado");
        }

        return erros;
    }

    // buscar dado cadastral existente este metodo consulta o campo informado na conta ou no perfil
    private boolean verificarExistente(String tipoUsuario, String tipoDado, String dado) {
        boolean existe = false;
        String sql = "";

        switch (tipoDado) {
            case "identificador":
                String tabela = tipoUsuario.equalsIgnoreCase("Aluno") ? "ALUNOS" : "PROFESSORES";
                String coluna = tipoUsuario.equalsIgnoreCase("Aluno") ? "rgm" : "matricula";

                sql = "SELECT 1 FROM public.\"USUARIOS\" u JOIN public.\"" + tabela + "\" p ON p.\"id_usuario\" = u.\"id\" "
                        + "WHERE p.\"" + coluna + "\" = ?";
                break;

            case "email":
                sql = "SELECT 1 FROM public.\"USUARIOS\" WHERE LOWER(\"email\") = ?";
                break;

            case "telefone":
                sql = "SELECT 1 FROM public.\"USUARIOS\" WHERE \"telefone\" = ?";
                break;

            default:
                throw new IllegalArgumentException("Tipo de dado inválido: " + tipoDado);
        }

        // consultar dado duplicado procura o valor informado e normaliza o email quando necessario
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, tipoDado.equals("email") ? normalizarEmail(dado) : dado);

            ResultSet rs = stmt.executeQuery();

            // detectar dado duplicado marca o dado como existente quando encontra um resultado
            if (rs.next()) {
                existe = true;
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return existe;
    }

    // consultar senha para login este metodo busca a senha protegida de uma conta ativa do perfil informado
    public String QueryLoginUsuario(String tipoUsuario, String EMAIL) throws SQLException {
        String emailNormalizado = normalizarEmail(EMAIL);
        String resultado = "";
        String sql = "";

        Connection conn = dataSource.getConnection();

        // validar login do aluno escolhe a consulta da senha de um aluno ativo
        if (tipoUsuario.equalsIgnoreCase("Aluno")) {
            sql = "SELECT u.\"senha\" FROM public.\"USUARIOS\" u "
                    + "JOIN public.\"ALUNOS\" a ON a.\"id_usuario\" = u.\"id\" "
                    + "WHERE LOWER(u.\"email\") = ? AND u.\"status\" = true";

        }
        // validar login do professor escolhe a consulta da senha de um professor ativo
        else if (tipoUsuario.equalsIgnoreCase("Professor")) {
            sql = "SELECT u.\"senha\" FROM public.\"USUARIOS\" u "
                    + "JOIN public.\"PROFESSORES\" p ON p.\"id_usuario\" = u.\"id\" "
                    + "WHERE LOWER(u.\"email\") = ? AND u.\"status\" = true";

        }
        // validar login do administrador escolhe a consulta da senha de um administrador ativo
        else if (tipoUsuario.equalsIgnoreCase("Administrador") || tipoUsuario.equalsIgnoreCase("Admin")) {
            sql = "SELECT u.\"senha\" FROM public.\"USUARIOS\" u "
                    + "JOIN public.\"ADMINISTRADOR\" a ON a.\"id_usuario\" = u.\"id\" "
                    + "WHERE LOWER(u.\"email\") = ? AND u.\"status\" = true";
        }

        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, emailNormalizado);

        ResultSet rs = stmt.executeQuery();

        // obter senha do login guarda a senha encontrada para o usuario
        if (rs.next()) {
            resultado = rs.getString("SENHA");
        }

        rs.close();
        stmt.close();
        conn.close();

        return resultado;
    }

    // buscar aluno ativo por email este metodo retorna os dados usados para criar a sessao do aluno
    public loginAlunoDTO buscarPorEmailAluno(String email) throws SQLException {
        String sql = "SELECT u.\"email\", u.\"senha\" FROM public.\"USUARIOS\" u "
                + "JOIN public.\"ALUNOS\" a ON a.\"id_usuario\" = u.\"id\" "
                + "WHERE LOWER(u.\"email\") = ? AND u.\"status\" = true";

        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setString(1, normalizarEmail(email));

        ResultSet rs = stmt.executeQuery();

        // buscar login do aluno retorna email e senha quando encontra o aluno
        if (rs.next()) {
            loginAlunoDTO usuario = new loginAlunoDTO();

            usuario.setEmail(rs.getString("EMAIL"));
            usuario.setSenha(rs.getString("SENHA"));

            rs.close();
            stmt.close();
            conn.close();

            return usuario;
        }

        rs.close();
        stmt.close();
        conn.close();

        return null;
    }

    // buscar professor ativo por email este metodo retorna os dados usados para criar a sessao do professor
    public loginProfessorDTO buscarPorEmailProfessor(String email) throws SQLException {
        String sql = "SELECT u.\"email\", u.\"senha\" FROM public.\"USUARIOS\" u "
                + "JOIN public.\"PROFESSORES\" p ON p.\"id_usuario\" = u.\"id\" "
                + "WHERE LOWER(u.\"email\") = ? AND u.\"status\" = true";

        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setString(1, normalizarEmail(email));

        ResultSet rs = stmt.executeQuery();

        // buscar login do professor retorna email e senha quando encontra o professor
        if (rs.next()) {
            loginProfessorDTO usuario = new loginProfessorDTO();

            usuario.setEmail(rs.getString("EMAIL"));
            usuario.setSenha(rs.getString("SENHA"));

            rs.close();
            stmt.close();
            conn.close();

            return usuario;
        }

        rs.close();
        stmt.close();
        conn.close();

        return null;
    }

    // buscar administrador ativo por email este metodo retorna os dados usados para criar a sessao administrativa
    public loginAdminDTO buscarPorEmailAdmin(String email) throws SQLException {
        String sql = "SELECT u.\"email\", u.\"senha\" FROM public.\"USUARIOS\" u "
                + "JOIN public.\"ADMINISTRADOR\" a ON a.\"id_usuario\" = u.\"id\" "
                + "WHERE LOWER(u.\"email\") = ? AND u.\"status\" = true";

        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setString(1, normalizarEmail(email));

        ResultSet rs = stmt.executeQuery();

        // buscar login do administrador retorna email e senha quando encontra o administrador
        if (rs.next()) {
            loginAdminDTO usuario = new loginAdminDTO();

            usuario.setEmail(rs.getString("EMAIL"));
            usuario.setSenha(rs.getString("SENHA"));

            rs.close();
            stmt.close();
            conn.close();

            return usuario;
        }

        rs.close();
        stmt.close();
        conn.close();

        return null;
    }

    // consultar perfil pelo email este metodo le o tipo de conta cadastrado no banco
    public String buscarPorTipoUsuario(String email) throws SQLException {
        String sql = "SELECT u.\"perfil\" FROM public.\"USUARIOS\" u WHERE LOWER(u.\"email\") = ?";

        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setString(1, normalizarEmail(email));

        ResultSet rs = stmt.executeQuery();

        // buscar perfil do usuario retorna o perfil encontrado e fecha a consulta
        if (rs.next()) {
            String perfil = rs.getString("perfil");

            rs.close();
            stmt.close();
            conn.close();

            return perfil;
        }

        rs.close();
        stmt.close();
        conn.close();

        return null;
    }

    // normalizar email da conta este metodo remove espacos das pontas e converte para minusculas
    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // gravar nova senha este metodo atualiza a senha do email informado e registra o resultado
    public void UpdateSenhaUsuario(String SENHA, String EMAIL) throws SQLException {
        Connection conn = dataSource.getConnection();

        String sql = "UPDATE public.\"USUARIOS\" SET \"senha\" = ? WHERE \"email\" = ?";

        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setString(1, SENHA);
        stmt.setString(2, EMAIL);

        logger.debug("Atualizando senha do usuário no banco.");

        // registro de detalhes confere se esta habilitado ao gravar nova senha
        if (logger.isDebugEnabled()) {
            // gravar historico ao gravar nova senha tenta salvar o registro no banco e informa falhas
            try {
                InserirLogsNoBD(
                        null,
                        "DEBUG",
                        UsuarioRepository.class.getName(),
                        "UpdateSenhaUsuario",
                        null,
                        "Atualizando senha do usuário no banco.",
                        null,
                        null
                );

            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        int linhas = stmt.executeUpdate();

        logger.debug("Linhas afetadas: {}", linhas);

        // registro de detalhes confere se esta habilitado ao gravar nova senha
        if (logger.isDebugEnabled()) {
            // gravar historico ao gravar nova senha tenta salvar o registro no banco e informa falhas
            try {
                InserirLogsNoBD(
                        null,
                        "DEBUG",
                        UsuarioRepository.class.getName(),
                        "UpdateSenhaUsuario",
                        null,
                        MessageFormatter.arrayFormat(
                                "Linhas afetadas: {}",
                                new Object[]{linhas}
                        ).getMessage(),
                        null,
                        null
                );

            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        stmt.close();
        conn.close();
    }
}