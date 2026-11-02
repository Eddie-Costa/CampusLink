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

@Repository
public class AlunoRepository {

    private static final Logger logger = LoggerFactory.getLogger(AlunoRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transacao;
    private final UsuarioRepository UsuarioRepository;

    public AlunoRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager,
                           UsuarioRepository UsuarioRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.transacao = new TransactionTemplate(transactionManager);
        // preserva a transacao independente que cada gravacao abria no dao
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.UsuarioRepository = UsuarioRepository;
    }

    public loginAlunoDTO buscarPorEmailAluno(String email) throws SQLException {
        String sql = """
                SELECT "email", "senha"
                FROM public."USUARIOS"
                WHERE "email" = ?
                """;

        registrarConsulta("Consultando credenciais do aluno no banco.");

        try {
            loginAlunoDTO aluno = jdbcTemplate.query(sql, rs -> {
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
            if (logger.isErrorEnabled()) {
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

    public String buscarPorIDAluno(String email) throws SQLException {
        String sql = "SELECT a.id FROM public.\"ALUNOS\" a JOIN public.\"USUARIOS\" u ON a.id_usuario = u.id WHERE u.email = ?";
        try {
            return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString("id") : "", email);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public void InsertAluno_TurmaIntoBD(String idAluno, String idTurma) throws SQLException {
        String sql = "INSERT INTO public.\"ALUNO_TURMA\" (\"id_aluno\", \"id_turma\", \"situacao_aluno\") VALUES (?, ?, ?)";
        try {
            transacao.executeWithoutResult(status -> {
                if (jdbcTemplate.update(sql, Long.parseLong(idAluno), Long.parseLong(idTurma), "verde") == 0) {
                    throw new DataIntegrityViolationException("Nenhum professor_turma foi inserido.");
                }
            });
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    public void removeAluno_TurmaIntoBD(String idAluno, String idTurma) throws SQLException {
        String sql = "DELETE FROM public.\"ALUNO_TURMA\" WHERE \"id_aluno\" = ? AND \"id_turma\" = ?";
        try {
            transacao.executeWithoutResult(status ->
                    jdbcTemplate.update(sql, Long.parseLong(idAluno), Long.parseLong(idTurma)));
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    private void registrarConsulta(String mensagem) {
        logger.debug(mensagem);
        if (logger.isDebugEnabled()) {
            try {
                UsuarioRepository.InserirLogsNoBD(null, "DEBUG", AlunoRepository.class.getName(), "buscarPorEmailAluno", null,
                        mensagem, null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }

    // mantem o contrato de erro usado pelos controllers atuais
    private SQLException converterErro(RuntimeException erro) {
        Throwable causa = erro;
        while (causa != null) {
            if (causa instanceof SQLException sqlException) {
                return sqlException;
            }
            causa = causa.getCause();
        }
        return new SQLException(erro.getMessage(), erro);
    }
}
