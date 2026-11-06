// regras de denuncias controlam o registro de relatos sobre materiais
package com.example.CampusLink.service;

import org.slf4j.helpers.MessageFormatter;
import java.io.StringWriter;
import java.io.PrintWriter;
import com.example.CampusLink.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.DenunciaRepository;
import com.example.CampusLink.dto.DenunciaDTO;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

// servico de relatos esta classe impede repeticoes e calcula quando suspender um conteudo
@Service
public class DenunciaService {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(DenunciaService.class);

    private final DenunciaRepository denunciaRepository;
    private final ConteudoRepository conteudoRepository;

    // preparar regras das denuncias este construtor recebe as operacoes de relatos e conteudos
    public DenunciaService(
            DenunciaRepository denunciaRepository,
            ConteudoRepository conteudoRepository
    ) {
        this.denunciaRepository = denunciaRepository;
        this.conteudoRepository = conteudoRepository;
    }

    // registrar denuncia validada este metodo evita repeticoes e suspende o material ao atingir o limite de relatos
    public String registrarDenuncia(Long idConteudo, Long idAluno, Long idTurma, String motivo) {

        // validar denuncia exige os codigos do conteudo do aluno e da turma
        if (idConteudo == null || idAluno == null || idTurma == null) {
            logger.warn("Denúncia recusada: identificadores ausentes. conteudoId={} alunoId={} turmaId={}", idConteudo, idAluno, idTurma);
            // registro de avisos confere se esta habilitado ao registrar denuncia validada
            if (logger.isWarnEnabled()) {
                // gravar historico ao registrar denuncia validada tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                            MessageFormatter.arrayFormat("Denúncia recusada: identificadores ausentes. conteudoId={} alunoId={} turmaId={}", new Object[]{idConteudo, idAluno, idTurma}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "erro";
        }

        // validar motivo da denuncia rejeita uma justificativa vazia
        if (motivo == null || motivo.isBlank()) {
            logger.warn("Denúncia recusada: motivo vazio. conteudoId={} alunoId={}", idConteudo, idAluno);
            // registro de avisos confere se esta habilitado ao registrar denuncia validada
            if (logger.isWarnEnabled()) {
                // gravar historico ao registrar denuncia validada tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                            MessageFormatter.arrayFormat("Denúncia recusada: motivo vazio. conteudoId={} alunoId={}", new Object[]{idConteudo, idAluno}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "motivo_vazio";
        }

        // registrar denuncia confere o conteudo e o aluno antes de salvar e avaliar a suspensao
        try {

            String statusConteudo = conteudoRepository.buscarStatus(idConteudo);

            // conferir conteudo denunciado informa quando o material nao foi encontrado
            if (statusConteudo == null) {
                logger.warn("Conteúdo não encontrado para denúncia. conteudoId={}", idConteudo);
                // registro de avisos confere se esta habilitado ao registrar denuncia validada
                if (logger.isWarnEnabled()) {
                    // gravar historico ao registrar denuncia validada tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                                MessageFormatter.arrayFormat("Conteúdo não encontrado para denúncia. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                return "conteudo_nao_encontrado";
            }

            // validar situacao do conteudo permite denunciar apenas conteudos ativos
            if (!statusConteudo.equalsIgnoreCase("ativo")) {
                logger.warn("Conteúdo indisponível para denúncia. conteudoId={} status={}", idConteudo, statusConteudo);
                // registro de avisos confere se esta habilitado ao registrar denuncia validada
                if (logger.isWarnEnabled()) {
                    // gravar historico ao registrar denuncia validada tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                                MessageFormatter.arrayFormat("Conteúdo indisponível para denúncia. conteudoId={} status={}", new Object[]{idConteudo, statusConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                return "conteudo_indisponivel";
            }

            boolean jaDenunciou = denunciaRepository.alunoJaDenunciou(idConteudo, idAluno);

            // impedir denuncia duplicada informa quando o aluno ja denunciou o material
            if (jaDenunciou) {
                logger.warn("Denúncia duplicada. conteudoId={} alunoId={}", idConteudo, idAluno);
                // registro de avisos confere se esta habilitado ao registrar denuncia validada
                if (logger.isWarnEnabled()) {
                    // gravar historico ao registrar denuncia validada tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                                MessageFormatter.arrayFormat("Denúncia duplicada. conteudoId={} alunoId={}", new Object[]{idConteudo, idAluno}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                return "duplicada";
            }

            Long idProfessor = conteudoRepository.buscarIdProfessor(idConteudo);

            // conferir professor do conteudo impede a denuncia quando o professor nao foi encontrado
            if (idProfessor == null) {
                logger.warn("Professor não encontrado para denúncia. conteudoId={}", idConteudo);
                // registro de avisos confere se esta habilitado ao registrar denuncia validada
                if (logger.isWarnEnabled()) {
                    // gravar historico ao registrar denuncia validada tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                                MessageFormatter.arrayFormat("Professor não encontrado para denúncia. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                return "professor_nao_encontrado";
            }

            int totalAlunos = denunciaRepository.contarAlunosDaTurma(idTurma);

            // conferir alunos da turma impede a denuncia em turma sem alunos
            if (totalAlunos <= 0) {
                logger.warn("Denúncia recusada: turma sem alunos. turmaId={}", idTurma);
                // registro de avisos confere se esta habilitado ao registrar denuncia validada
                if (logger.isWarnEnabled()) {
                    // gravar historico ao registrar denuncia validada tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                                MessageFormatter.arrayFormat("Denúncia recusada: turma sem alunos. turmaId={}", new Object[]{idTurma}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                return "turma_sem_alunos";
            }

            DenunciaDTO denuncia = new DenunciaDTO();
            denuncia.setIdConteudo(idConteudo);
            denuncia.setIdAluno(idAluno);
            denuncia.setIdProfessor(idProfessor);
            denuncia.setMotivo(motivo.trim());
            denunciaRepository.inserir(denuncia);
            logger.info("Denúncia registrada. conteudoId={} alunoId={} turmaId={}", idConteudo, idAluno, idTurma);
            // registro de informacoes confere se esta habilitado ao registrar denuncia validada
            if (logger.isInfoEnabled()) {
                // gravar historico ao registrar denuncia validada tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", DenunciaService.class.getName(), "registrarDenuncia", null,
                            MessageFormatter.arrayFormat("Denúncia registrada. conteudoId={} alunoId={} turmaId={}", new Object[]{idConteudo, idAluno, idTurma}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            int quantidadeDenuncias =
                    denunciaRepository.contarDenunciasPendentes(
                            idConteudo
                    );

            int limiteDenuncias =
                    (int) Math.ceil(
                            totalAlunos * 0.15
                    );

            // definir minimo de denuncias exige pelo menos duas denuncias para suspender o conteudo
            if (limiteDenuncias < 2) {
                limiteDenuncias = 2;
            }

            // suspender conteudo denunciado suspende o material quando as denuncias atingem o limite
            if (quantidadeDenuncias >= limiteDenuncias) {

                conteudoRepository.atualizarStatus(idConteudo, "suspenso_denuncia");
                logger.info("Conteúdo suspenso automaticamente por denúncias. conteudoId={} quantidade={} limite={}", idConteudo, quantidadeDenuncias, limiteDenuncias);
                // registro de informacoes confere se esta habilitado ao registrar denuncia validada
                if (logger.isInfoEnabled()) {
                    // gravar historico ao registrar denuncia validada tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", DenunciaService.class.getName(), "registrarDenuncia", null,
                                MessageFormatter.arrayFormat("Conteúdo suspenso automaticamente por denúncias. conteudoId={} quantidade={} limite={}", new Object[]{idConteudo, quantidadeDenuncias, limiteDenuncias}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                return "suspenso";
            }

            return "sucesso";

        } catch (SQLException e) {
            logger.error("Erro ao registrar denúncia. conteudoId={} alunoId={} turmaId={}", idConteudo, idAluno, idTurma, e);
            // registro de erros confere se esta habilitado ao registrar denuncia validada
            if (logger.isErrorEnabled()) {
                // gravar historico ao registrar denuncia validada tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", DenunciaService.class.getName(), "registrarDenuncia", null,
                            MessageFormatter.arrayFormat("Erro ao registrar denúncia. conteudoId={} alunoId={} turmaId={}", new Object[]{idConteudo, idAluno, idTurma}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException("Não foi possível registrar a denúncia", e);
        }
    }

    // consultar total de denuncias pendentes este metodo conta os relatos do material e trata falhas
    public int contarDenuncias(Long idConteudo) {

        // contar denuncias consulta as denuncias pendentes do conteudo e trata falhas
        try {
            return denunciaRepository.contarDenunciasPendentes(idConteudo);

        } catch (SQLException e) {
            logger.error("Erro ao contar denúncias. conteudoId={}", idConteudo, e);
            // registro de erros confere se esta habilitado ao consultar total de denuncias pendentes
            if (logger.isErrorEnabled()) {
                // gravar historico ao consultar total de denuncias pendentes tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", DenunciaService.class.getName(), "contarDenuncias", null,
                            MessageFormatter.arrayFormat("Erro ao contar denúncias. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException("Não foi possível contar as denúncias", e);
        }
    }

    // consultar nomes dos denunciantes este metodo recupera os alunos que denunciaram o material e trata falhas
    public List<String> listarNomesAlunos(Long idConteudo) {

        // listar alunos das denuncias consulta os nomes dos alunos que denunciaram o conteudo
        try {
            return denunciaRepository.listarNomesAlunosPorConteudo(idConteudo);

        } catch (SQLException e) {
            logger.error("Erro ao consultar alunos das denúncias. conteudoId={}", idConteudo, e);
            // registro de erros confere se esta habilitado ao consultar nomes dos denunciantes
            if (logger.isErrorEnabled()) {
                // gravar historico ao consultar nomes dos denunciantes tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", DenunciaService.class.getName(), "listarNomesAlunos", null,
                            MessageFormatter.arrayFormat("Erro ao consultar alunos das denúncias. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException("Não foi possível listar os alunos", e);
        }
    }

    // consultar relatos do conteudo este metodo recupera as denuncias detalhadas e trata falhas
    public List<DenunciaDTO> listarPorConteudo(Long idConteudo) {

        // listar denuncias do conteudo consulta os registros e trata falhas no banco
        try {
            return denunciaRepository.listarPorConteudo(idConteudo);
        } catch (SQLException e) {
            logger.error("Erro ao consultar denúncias. conteudoId={}", idConteudo, e);
            // registro de erros confere se esta habilitado ao consultar relatos do conteudo
            if (logger.isErrorEnabled()) {
                // gravar historico ao consultar relatos do conteudo tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", DenunciaService.class.getName(), "listarPorConteudo", null,
                            MessageFormatter.arrayFormat("Erro ao consultar denúncias. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException("Não foi possível listar as denúncias do conteúdo", e);
        }
    }
}
