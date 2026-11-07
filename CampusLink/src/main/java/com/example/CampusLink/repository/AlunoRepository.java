// banco de alunos centraliza consultas de identidade e vinculos de turma
package com.example.CampusLink.repository;

import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;

// persistencia de alunos esta classe consulta credenciais e grava ou remove participacoes
@Repository
public class AlunoRepository {

    private static final Logger logger = LoggerFactory.getLogger(AlunoRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transacao;
    private final UsuarioRepository UsuarioRepository;

    // preparar consultas de aluno este construtor recebe o banco as gravacoes e o registro de historico
    public AlunoRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager,
                           UsuarioRepository UsuarioRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.transacao = new TransactionTemplate(transactionManager);

        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.UsuarioRepository = UsuarioRepository;
    }

    // buscar credenciais do aluno este metodo consulta email e senha e registra o resultado da busca
    public loginAlunoDTO buscarPorEmailAluno(String email) throws SQLException {
        String sql = """
                SELECT "email", "senha"
                FROM public."USUARIOS"
                WHERE "email" = ?
                """;

        registrarConsulta("Consultando credenciais do aluno no banco.");

        // consultar credenciais do aluno busca email e senha e registra falhas de acesso ao banco
        try {
            loginAlunoDTO aluno = jdbcTemplate.query(sql, rs -> {
                // tratar aluno nao encontrado retorna vazio quando a consulta nao possui resultado
                if (!rs.next()) {
                    return null;
                }
                loginAlunoDTO usuario = new loginAlunoDTO();
                usuario.setEmail(rs.getString("email"));
                usuario.setSenha(rs.getString("senha"));
                return usuario;
            }, email);

            registrarConsulta(aluno == null
                    ? "Nenhum aluno encontrado na consulta de credenciais."
                    : "Credenciais do aluno encontradas.");
            return aluno;
        } catch (DataAccessException erro) {
            SQLException e = converterErro(erro);
            logger.error("Erro ao buscar credenciais do aluno no banco.", e);
            // registro de erros confere se esta habilitado ao buscar credenciais do aluno
            if (logger.isErrorEnabled()) {
                // gravar historico ao buscar credenciais do aluno tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AlunoRepository.class.getName(), "buscarPorEmailAluno", null,
                            "Erro ao buscar credenciais do aluno no banco.", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }
    }

    // buscar identificador do aluno este metodo consulta o vinculo entre aluno e email do usuario
    public String buscarPorIDAluno(String email) throws SQLException {
        String sql = "SELECT a.id FROM public.\"ALUNOS\" a JOIN public.\"USUARIOS\" u ON a.id_usuario = u.id WHERE u.email = ?";
        // consultar codigo do aluno busca pelo email e converte falhas do banco
        try {
            return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString("id") : "", email);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // cadastrar vinculo de aluno este metodo insere a participacao na turma com situacao verde
    public void InsertAluno_TurmaIntoBD(String idAluno, String idTurma) throws SQLException {
        String sql = "INSERT INTO public.\"ALUNO_TURMA\" (\"id_aluno\", \"id_turma\", \"situacao_aluno\") VALUES (?, ?, ?)";
        // inserir participacao do aluno grava o vinculo em uma unica operacao e converte falhas
        try {
            transacao.executeWithoutResult(status -> {
                // conferir participacao criada interrompe a gravacao quando nenhum vinculo foi inserido
                if (jdbcTemplate.update(sql, Long.parseLong(idAluno), Long.parseLong(idTurma), "verde") == 0) {
                    throw new DataIntegrityViolationException("Nenhum professor_turma foi inserido.");
                }
            });
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    // delete vinculo direto de aluno este metodo apaga a relacao entre o aluno e a turma
    public void removeAluno_TurmaIntoBD(String idAluno, String idTurma) throws SQLException {
        String sql = "DELETE FROM public.\"ALUNO_TURMA\" WHERE \"id_aluno\" = ? AND \"id_turma\" = ?";
        // delete participacao academica remove o vinculo entre aluno e turma e converte falhas do banco
        try {
            transacao.executeWithoutResult(status ->
                    jdbcTemplate.update(sql, Long.parseLong(idAluno), Long.parseLong(idTurma)));
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    // registrar consulta de aluno este metodo grava a mensagem de acompanhamento da busca
    private void registrarConsulta(String mensagem) {
        logger.debug(mensagem);
        // registro de detalhes confere se esta habilitado ao registrar consulta de aluno
        if (logger.isDebugEnabled()) {
            // gravar historico ao registrar consulta de aluno tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "DEBUG", AlunoRepository.class.getName(), "buscarPorEmailAluno", null,
                        mensagem, null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }


    // tratar erro do banco de alunos este metodo recupera a falha original ou cria uma falha de consulta
    private SQLException converterErro(RuntimeException erro) {
        Throwable causa = erro;
        while (causa != null) {
            // recuperar falha do banco devolve a causa original quando ela e uma falha de consulta
            if (causa instanceof SQLException sqlException) {
                return sqlException;
            }
            causa = causa.getCause();
        }
        return new SQLException(erro.getMessage(), erro);
    }
}
