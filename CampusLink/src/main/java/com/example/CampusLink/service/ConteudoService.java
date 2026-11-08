// regras de materiais coordenam publicacao progresso e moderacao de conteudos

package com.example.CampusLink.service;

import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.ConteudoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// servico de conteudos esta classe define tipo e duracao e valida alteracoes de conclusao e revisao
@Service
public class ConteudoService {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(ConteudoService.class);

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String MENSAGEM_DURACAO_INVALIDA =
            "Informe uma duração válida em minutos, horas ou no formato MM:SS ou HH:MM:SS.";

    private final ConteudoRepository conteudoRepository;
    private final ArquivoService arquivoService;

    // preparar regras dos conteudos este construtor recebe as operacoes de materiais e arquivos
    public ConteudoService(ConteudoRepository conteudoRepository, ArquivoService arquivoService) {
        this.conteudoRepository = conteudoRepository;
        this.arquivoService = arquivoService;
    }

    // criar material completo este metodo define tipo e duracao grava o conteudo e trata falhas no anexo
    public ConteudoDTO criar(ConteudoDTO conteudo, MultipartFile arquivo) {

        // definir prioridade do conteudo usa prioridade media quando nenhuma foi informada
        if (conteudo.getPrioridade() == null || conteudo.getPrioridade().isBlank()) {
            conteudo.setPrioridade("media");
        }

        conteudo.setTipo(definirTipo(conteudo, arquivo));
        conteudo.setDuracaoEstimadaMin(converterDuracaoEmMinutos(conteudo.getDuracaoEstimada()));

        // cadastrar conteudo tenta gravar os dados e avisa quando o banco falha
        try {
            conteudoRepository.inserir(conteudo);

        } catch (SQLException e) {
            logger.error("Erro ao cadastrar conteúdo. turmaId={} professorId={}", conteudo.getIdTurma(), conteudo.getIdProfessor(), e);

            // registro de erros confere se esta habilitado ao criar material completo
            if (logger.isErrorEnabled()) {
                // gravar historico ao criar material completo tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "criar", null,
                            MessageFormatter.arrayFormat("Erro ao cadastrar conteúdo. turmaId={} professorId={}", new Object[]{conteudo.getIdTurma(), conteudo.getIdProfessor()}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível cadastrar o conteúdo.", e);
        }

        // salvar anexo do conteudo inicia o envio quando existe um arquivo preenchido
        if (arquivo != null && !arquivo.isEmpty()) {
            // enviar anexo do conteudo tenta salvar o arquivo e desfaz o cadastro do conteudo se o envio falhar
            try {
                arquivoService.salvar(arquivo, conteudo.getId());

            } catch (RuntimeException e) {
                // delete conteudo sem anexo tenta apagar o conteudo criado quando o arquivo falha
                try {
                    conteudoRepository.excluir(conteudo.getId());

                    logger.info("Conteúdo removido após falha no arquivo. conteudoId={} turmaId={}", conteudo.getId(), conteudo.getIdTurma());

                    // registro de informacoes confere se esta habilitado ao criar material completo
                    if (logger.isInfoEnabled()) {
                        // gravar historico ao criar material completo tenta salvar o registro no banco e informa falhas
                        try {
                            UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "criar", null,
                                    MessageFormatter.arrayFormat("Conteúdo removido após falha no arquivo. conteudoId={} turmaId={}", new Object[]{conteudo.getId(), conteudo.getIdTurma()}).getMessage(), null, null);

                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                } catch (SQLException erroRemocao) {
                    logger.error("Erro ao remover conteúdo após falha no arquivo. conteudoId={} turmaId={}", conteudo.getId(), conteudo.getIdTurma(), erroRemocao);

                    // registro de erros confere se esta habilitado ao criar material completo
                    if (logger.isErrorEnabled()) {
                        // gravar historico ao criar material completo tenta salvar o registro no banco e informa falhas
                        try {
                            StringWriter excecaoLogBD = new StringWriter();
                            erroRemocao.printStackTrace(new PrintWriter(excecaoLogBD));

                            UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "criar", null,
                                    MessageFormatter.arrayFormat("Erro ao remover conteúdo após falha no arquivo. conteudoId={} turmaId={}", new Object[]{conteudo.getId(), conteudo.getIdTurma()}).getMessage(), null, excecaoLogBD.toString());

                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }
                }

                throw e;
            }
        }

        logger.info("Conteúdo cadastrado. conteudoId={} turmaId={} professorId={}", conteudo.getId(), conteudo.getIdTurma(), conteudo.getIdProfessor());

        // registro de informacoes confere se esta habilitado ao criar material completo
        if (logger.isInfoEnabled()) {
            // gravar historico ao criar material completo tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "criar", null,
                        MessageFormatter.arrayFormat("Conteúdo cadastrado. conteudoId={} turmaId={} professorId={}", new Object[]{conteudo.getId(), conteudo.getIdTurma(), conteudo.getIdProfessor()}).getMessage(), null, null);

            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        return conteudo;
    }

    // listar materiais da turma pelo servico este metodo consulta os conteudos e trata falhas do banco
    public List<ConteudoDTO> listarPorTurma(Long idTurma) {
        // listar conteudos da turma consulta os materiais e trata falhas no banco
        try {
            return conteudoRepository.listarPorTurma(idTurma);

        } catch (SQLException e) {
            logger.error("Erro ao listar conteúdos. turmaId={}", idTurma, e);

            // registro de erros confere se esta habilitado ao listar materiais da turma pelo servico
            if (logger.isErrorEnabled()) {
                // gravar historico ao listar materiais da turma pelo servico tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "listarPorTurma", null,
                            MessageFormatter.arrayFormat("Erro ao listar conteúdos. turmaId={}", new Object[]{idTurma}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível listar os conteúdos da turma.", e);
        }
    }
    // buscar materiais com filtros validos este metodo limita o texto de busca e confere o tipo antes da consulta
    public List<ConteudoDTO> buscarPorTurma(Long idTurma, String termo, String tipo) {

        String termoLimpo = (termo == null) ? null : termo.trim();

        // limitar busca de conteudos corta o termo de busca em cem caracteres
        if (termoLimpo != null && termoLimpo.length() > 100) {
            termoLimpo = termoLimpo.substring(0, 100);
        }

        String tipoLimpo = null;
        // validar filtro de conteudo aceita apenas os tipos de material conhecidos
        if (tipo != null && List.of("link", "video", "livro", "arquivo", "texto").contains(tipo.toLowerCase())) {
            tipoLimpo = tipo.toLowerCase();
        }

        // buscar conteudos da turma consulta os filtros informados e trata falhas na busca
        try {
            return conteudoRepository.buscarPorTurma(idTurma, termoLimpo, tipoLimpo);

        } catch (SQLException e) {
            logger.error("Erro ao buscar conteúdos. turmaId={}", idTurma, e);
            throw new RuntimeException("Não foi possível buscar os conteúdos da turma.", e);
        }
    }

    // listar materiais do professor pelo servico este metodo consulta os conteudos do autor e trata falhas
    public List<ConteudoDTO> listarPorProfessor(Long idProfessor) {
        // listar conteudos do professor consulta os materiais do professor e trata falhas
        try {
            return conteudoRepository.listarPorProfessor(idProfessor);

        } catch (SQLException e) {
            logger.error("Erro ao listar conteúdos do professor. professorId={}", idProfessor, e);

            // registro de erros confere se esta habilitado ao listar materiais do professor pelo servico
            if (logger.isErrorEnabled()) {
                // gravar historico ao listar materiais do professor pelo servico tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "listarPorProfessor", null,
                            MessageFormatter.arrayFormat("Erro ao listar conteúdos do professor. professorId={}", new Object[]{idProfessor}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível listar os conteúdos do professor.", e);
        }
    }

    // consultar pagina do professor este metodo encaminha a busca filtrada dos materiais do autor
    public List<ConteudoDTO> listarPorProfessorPaginado(Long idProfessor, int pagina, String busca, String status) throws SQLException {
        return conteudoRepository.listarPorProfessorPaginado(idProfessor, pagina, busca, status);
    }

    // consultar total filtrado do professor este metodo encaminha a contagem dos materiais da busca
    public int contarFiltradosPorProfessor(Long idProfessor, String busca, String status) throws SQLException {
        return conteudoRepository.contarFiltradosPorProfessor(idProfessor, busca, status);
    }

    // consultar totais do professor este metodo recupera as quantidades de materiais por situacao
    public Map<String, Integer> contarPorProfessor(Long idProfessor) throws SQLException {
        return conteudoRepository.contarPorProfessor(idProfessor);
    }


    // consultar pagina administrativa de conteudos este metodo encaminha a busca filtrada dos materiais
    public List<ConteudoDTO> listarTodosParaAdminPaginado(int pagina, String busca, String status) throws SQLException {
        return conteudoRepository.listarTodosParaAdminPaginado(pagina, busca, status);
    }


    // consultar total administrativo filtrado este metodo recupera a quantidade de materiais da busca
    public int contarFiltradosParaAdmin(String busca, String status) throws SQLException {
        return conteudoRepository.contarFiltradosParaAdmin(busca, status);
    }

    // consultar fila de analise este metodo lista os materiais enviados para revisao e trata falhas
    public List<ConteudoDTO> listarParaAnaliseAdmin() {
        // listar conteudos em analise consulta os materiais enviados para revisao e trata falhas
        try {
            return conteudoRepository.listarParaAnaliseAdmin();

        } catch (SQLException e) {
            throw new RuntimeException("Não foi possível listar os conteúdos enviados para análise.", e);
        }
    }

    // delete material do professor pelo servico este metodo solicita a remocao pelo autor e registra o resultado
    public boolean removerConteudoProfessor(Long idConteudo, Long idProfessor) {
        // delete conteudo do professor tenta retirar o material e registra o resultado
        try {
            boolean removido = conteudoRepository.removerConteudoProfessor(idConteudo, idProfessor);

            // conferir delete conteudo registra se a exclusao foi realizada ou recusada
            if (removido) {
                logger.info("Conteúdo removido pelo professor. conteudoId={} professorId={}", idConteudo, idProfessor);

                // registro de informacoes confere se esta habilitado ao delete material do professor pelo servico
                if (logger.isInfoEnabled()) {
                    // gravar historico ao delete material do professor pelo servico tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "removerConteudoProfessor", null,
                                MessageFormatter.arrayFormat("Conteúdo removido pelo professor. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

            } else {
                logger.warn("Remoção de conteúdo recusada. conteudoId={} professorId={}", idConteudo, idProfessor);

                // registro de avisos confere se esta habilitado ao delete material do professor pelo servico
                if (logger.isWarnEnabled()) {
                    // gravar historico ao delete material do professor pelo servico tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ConteudoService.class.getName(), "removerConteudoProfessor", null,
                                MessageFormatter.arrayFormat("Remoção de conteúdo recusada. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }

            return removido;

        } catch (SQLException e) {
            logger.error("Erro ao remover conteúdo do professor. conteudoId={} professorId={}", idConteudo, idProfessor, e);

            // registro de erros confere se esta habilitado ao delete material do professor pelo servico
            if (logger.isErrorEnabled()) {
                // gravar historico ao delete material do professor pelo servico tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "removerConteudoProfessor", null,
                            MessageFormatter.arrayFormat("Erro ao remover conteúdo do professor. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível remover o conteúdo.", e);
        }
    }

    // reabrir material pelo servico este metodo solicita a reabertura autorizada e registra o resultado
    public boolean reabrir(Long idConteudo, Long idProfessor, Long idTurma) {
        // reabrir conteudo tenta disponibilizar o material novamente e registra o resultado
        try {
            boolean reaberto = conteudoRepository.reabrir(idConteudo, idProfessor, idTurma);

            // conferir reabertura do conteudo registra se a reabertura foi realizada ou recusada
            if (reaberto) {
                logger.info("Conteúdo re-disponibilizado. conteudoId={} professorId={}", idConteudo, idProfessor);

                // registro de informacoes confere se esta habilitado ao reabrir material pelo servico
                if (logger.isInfoEnabled()) {
                    // gravar historico ao reabrir material pelo servico tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "reabrir", null,
                                MessageFormatter.arrayFormat("Conteúdo re-disponibilizado. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

            } else {
                logger.warn("Re-disponibilização recusada. conteudoId={} professorId={}", idConteudo, idProfessor);

                // registro de avisos confere se esta habilitado ao reabrir material pelo servico
                if (logger.isWarnEnabled()) {
                    // gravar historico ao reabrir material pelo servico tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ConteudoService.class.getName(), "reabrir", null,
                                MessageFormatter.arrayFormat("Re-disponibilização recusada. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }

            return reaberto;

        } catch (SQLException e) {
            logger.error("Erro ao re-disponibilizar conteúdo. conteudoId={} professorId={}", idConteudo, idProfessor, e);

            // registro de erros confere se esta habilitado ao reabrir material pelo servico
            if (logger.isErrorEnabled()) {
                // gravar historico ao reabrir material pelo servico tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "reabrir", null,
                            MessageFormatter.arrayFormat("Erro ao re-disponibilizar conteúdo. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível re-disponibilizar o conteúdo.", e);
        }
    }

    // solicitar analise do material pelo servico este metodo encaminha a revisao do autor e registra o resultado
    public boolean solicitarRevisao(Long idConteudo, Long idProfessor) {
        // solicitar revisao do conteudo tenta enviar o material para analise e registra o resultado
        try {
            boolean solicitado = conteudoRepository.solicitarRevisao(idConteudo, idProfessor);

            // conferir pedido de revisao registra se a solicitacao foi aceita ou recusada
            if (solicitado) {
                logger.info("Revisão de conteúdo solicitada. conteudoId={} professorId={}", idConteudo, idProfessor);

                // registro de informacoes confere se esta habilitado ao solicitar analise do material pelo servico
                if (logger.isInfoEnabled()) {
                    // gravar historico ao solicitar analise do material pelo servico tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "solicitarRevisao", null,
                                MessageFormatter.arrayFormat("Revisão de conteúdo solicitada. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

            } else {
                logger.warn("Solicitação de revisão recusada. conteudoId={} professorId={}", idConteudo, idProfessor);

                // registro de avisos confere se esta habilitado ao solicitar analise do material pelo servico
                if (logger.isWarnEnabled()) {
                    // gravar historico ao solicitar analise do material pelo servico tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ConteudoService.class.getName(), "solicitarRevisao", null,
                                MessageFormatter.arrayFormat("Solicitação de revisão recusada. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }

            return solicitado;

        } catch (SQLException e) {
            logger.error("Erro ao solicitar revisão. conteudoId={} professorId={}", idConteudo, idProfessor, e);

            // registro de erros confere se esta habilitado ao solicitar analise do material pelo servico
            if (logger.isErrorEnabled()) {
                // gravar historico ao solicitar analise do material pelo servico tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "solicitarRevisao", null,
                            MessageFormatter.arrayFormat("Erro ao solicitar revisão. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível solicitar a análise.", e);
        }
    }

    // verificar turma do conteudo este metodo confere se o conteudo pertence a turma informada
    public boolean pertenceATurma(Long idConteudo, Long idTurma) {
        // consultar turma do conteudo converte falhas do banco em erro da aplicacao
        try {
            return conteudoRepository.conteudoPertenceTurma(idConteudo, idTurma);
        } catch (SQLException e) {
            throw new RuntimeException("Não foi possível verificar a turma do conteúdo.", e);
        }
    }

    // delete material pelo servico este metodo marca o conteudo como removido e registra o resultado
    public void excluir(Long id) {
        // delete conteudo tenta apagar o material e registra falhas
        try {
            conteudoRepository.excluir(id);

            logger.info("Conteúdo excluído. conteudoId={}", id);

            // registro de informacoes confere se esta habilitado ao delete material pelo servico
            if (logger.isInfoEnabled()) {
                // gravar historico ao delete material pelo servico tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Conteúdo excluído. conteudoId={}", new Object[]{id}).getMessage(), null, null);

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao excluir conteúdo. conteudoId={}", id, e);

            // registro de erros confere se esta habilitado ao delete material pelo servico
            if (logger.isErrorEnabled()) {
                // gravar historico ao delete material pelo servico tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Erro ao excluir conteúdo. conteudoId={}", new Object[]{id}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível excluir o conteúdo.", e);
        }
    }

    // editar material pelo servico este metodo atualiza tipo duracao e dados do conteudo
    public ConteudoDTO atualizar(ConteudoDTO conteudo) {
        conteudo.setTipo(definirTipo(conteudo, null));
        conteudo.setDuracaoEstimadaMin(converterDuracaoEmMinutos(conteudo.getDuracaoEstimada()));

        // editar conteudo tenta atualizar os dados do material e registra falhas
        try {
            conteudoRepository.atualizar(conteudo);

            logger.info("Conteúdo atualizado. conteudoId={}", conteudo.getId());

            // registro de informacoes confere se esta habilitado ao editar material pelo servico
            if (logger.isInfoEnabled()) {
                // gravar historico ao editar material pelo servico tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "atualizar", null,
                            MessageFormatter.arrayFormat("Conteúdo atualizado. conteudoId={}", new Object[]{conteudo.getId()}).getMessage(), null, null);

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao atualizar conteúdo. conteudoId={}", conteudo.getId(), e);

            // registro de erros confere se esta habilitado ao editar material pelo servico
            if (logger.isErrorEnabled()) {
                // gravar historico ao editar material pelo servico tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "atualizar", null,
                            MessageFormatter.arrayFormat("Erro ao atualizar conteúdo. conteudoId={}", new Object[]{conteudo.getId()}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível atualizar o conteúdo.", e);
        }

        return conteudo;
    }

    // alterar conclusao validada este metodo confere a turma e o prazo antes de marcar ou desmarcar o material
    public void alterarConclusao(Long idAluno, Long idTurma, Long idConteudo, boolean concluido) {
        // alterar conclusao do conteudo confere turma e prazo antes de salvar o progresso
        try {
            // validar turma do conteudo impede alterar progresso de um conteudo fora da turma
            if (!conteudoRepository.conteudoPertenceTurma(idConteudo, idTurma)) {
                logger.warn("Conclusão recusada: conteúdo fora da turma. alunoId={} turmaId={} conteudoId={}", idAluno, idTurma, idConteudo);

                // registro de avisos confere se esta habilitado ao alterar conclusao validada
                if (logger.isWarnEnabled()) {
                    // gravar historico ao alterar conclusao validada tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ConteudoService.class.getName(), "alterarConclusao", null,
                                MessageFormatter.arrayFormat("Conclusão recusada: conteúdo fora da turma. alunoId={} turmaId={} conteudoId={}", new Object[]{idAluno, idTurma, idConteudo}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                throw new IllegalArgumentException("Conteúdo não encontrado nesta turma.");
            }

            LocalDateTime prazoEncerrado = concluido ? listarPrazosEncerrados(List.of(idConteudo)).get(idConteudo) : null;

            // validar prazo do conteudo impede concluir um material com prazo encerrado
            if (prazoEncerrado != null) {
                logger.warn("Conclusão recusada: prazo encerrado. alunoId={} conteudoId={} prazo={}", idAluno, idConteudo, prazoEncerrado);

                // registro de avisos confere se esta habilitado ao alterar conclusao validada
                if (logger.isWarnEnabled()) {
                    // gravar historico ao alterar conclusao validada tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ConteudoService.class.getName(), "alterarConclusao", null,
                                MessageFormatter.arrayFormat("Conclusão recusada: prazo encerrado. alunoId={} conteudoId={} prazo={}", new Object[]{idAluno, idConteudo, prazoEncerrado}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                throw new IllegalArgumentException("O prazo deste conteúdo terminou em "
                        + prazoEncerrado.format(DateTimeFormatter.ofPattern("dd/MM"))
                        + ". Ele não pode mais ser concluído.");
            }

            // salvar conclusao do conteudo marca ou desmarca o material conforme o pedido
            if (concluido) {
                conteudoRepository.marcarConcluido(idAluno, idConteudo);
            } else {
                conteudoRepository.desmarcarConcluido(idAluno, idConteudo);
            }

            logger.info("Conclusão de conteúdo alterada. alunoId={} conteudoId={} concluido={}", idAluno, idConteudo, concluido);

            // registro de informacoes confere se esta habilitado ao alterar conclusao validada
            if (logger.isInfoEnabled()) {
                // gravar historico ao alterar conclusao validada tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "alterarConclusao", null,
                            MessageFormatter.arrayFormat("Conclusão de conteúdo alterada. alunoId={} conteudoId={} concluido={}", new Object[]{idAluno, idConteudo, concluido}).getMessage(), null, null);

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao alterar conclusão do conteúdo. alunoId={} conteudoId={}", idAluno, idConteudo, e);

            // registro de erros confere se esta habilitado ao alterar conclusao validada
            if (logger.isErrorEnabled()) {
                // gravar historico ao alterar conclusao validada tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "alterarConclusao", null,
                            MessageFormatter.arrayFormat("Erro ao alterar conclusão do conteúdo. alunoId={} conteudoId={}", new Object[]{idAluno, idConteudo}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível atualizar o progresso do conteúdo.", e);
        }
    }

    // consultar conclusoes da turma pelo servico este metodo recupera o progresso do aluno e trata falhas
    public Set<Long> listarConcluidosDoAluno(Long idAluno, Long idTurma) {
        // listar conteudos concluidos consulta o progresso do aluno na turma e trata falhas
        try {
            return conteudoRepository.listarConcluidosDoAluno(idAluno, idTurma);

        } catch (SQLException e) {
            logger.error("Erro ao listar conteúdos concluídos. alunoId={} turmaId={}", idAluno, idTurma, e);

            // registro de erros confere se esta habilitado ao consultar conclusoes da turma pelo servico
            if (logger.isErrorEnabled()) {
                // gravar historico ao consultar conclusoes da turma pelo servico tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "listarConcluidosDoAluno", null,
                            MessageFormatter.arrayFormat("Erro ao listar conteúdos concluídos. alunoId={} turmaId={}", new Object[]{idAluno, idTurma}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível listar os conteúdos concluídos.", e);
        }
    }

    // consultar prazos com horario atual este metodo verifica os prazos dos materiais no horario de sao paulo
    public Map<Long, LocalDateTime> listarPrazosEncerrados(List<Long> idsConteudos) {
        // listar prazos encerrados consulta os prazos dos materiais considerando o horario atual
        try {
            return conteudoRepository.listarPrazosEncerrados(idsConteudos, LocalDateTime.now(FUSO));

        } catch (SQLException e) {
            logger.error("Erro ao listar prazos encerrados. conteudos={}", idsConteudos, e);

            // registro de erros confere se esta habilitado ao consultar prazos com horario atual
            if (logger.isErrorEnabled()) {
                // gravar historico ao consultar prazos com horario atual tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "listarPrazosEncerrados", null,
                            MessageFormatter.arrayFormat("Erro ao listar prazos encerrados. conteudos={}", new Object[]{idsConteudos}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível verificar os prazos dos conteúdos.", e);
        }
    }

    // converter duracao em minutos interpreta os formatos aceitos e rejeita valores que nao podem ser convertidos
    private Integer converterDuracaoEmMinutos(String texto) {
        // converter duracao ignora texto ausente ou vazio
        if (texto == null || texto.isBlank()) {
            return null;
        }

        String valor = texto.trim().toLowerCase(Locale.ROOT);

        // calcular minutos converte a duracao e informa quando o valor excede o limite aceito
        try {
            long minutos;

            // ler duracao em minutos usa o numero inteiro informado
            if (valor.matches("\\d+")) {
                minutos = Long.parseLong(valor);
            }
            // ler duracao com horas converte horas minutos e segundos para minutos arredondados para cima
            else if (valor.matches("\\d+:\\d{2}:\\d{2}")) {
                String[] partes = valor.split(":");
                long segundos = Math.addExact(
                        Math.addExact(Math.multiplyExact(Long.parseLong(partes[0]), 3600L),
                                Math.multiplyExact(Long.parseLong(partes[1]), 60L)),
                        Long.parseLong(partes[2]));
                minutos = segundos / 60 + (segundos % 60 == 0 ? 0 : 1);
            }
            // ler duracao com segundos converte minutos e segundos para minutos arredondados para cima
            else if (valor.matches("\\d+:\\d{2}")) {
                String[] partes = valor.split(":");
                long segundos = Math.addExact(
                        Math.multiplyExact(Long.parseLong(partes[0]), 60L),
                        Long.parseLong(partes[1]));
                minutos = segundos / 60 + (segundos % 60 == 0 ? 0 : 1);
            }
            // ler duracao em horas multiplica a quantidade de horas por sessenta
            else if (valor.matches("\\d+\\s*(h|hora|horas)")) {
                minutos = Math.multiplyExact(Long.parseLong(valor.replaceAll("\\D", "")), 60L);
            }
            // ler duracao com unidade de minutos extrai o numero informado
            else if (valor.matches("\\d+\\s*(min|minuto|minutos)")) {
                minutos = Long.parseLong(valor.replaceAll("\\D", ""));
            } else {
                throw new IllegalArgumentException(MENSAGEM_DURACAO_INVALIDA);
            }

            // exigir duracao positiva e representavel impede gravar valores invalidos ou excedidos
            if (minutos <= 0 || minutos > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(MENSAGEM_DURACAO_INVALIDA);
            }

            return (int) minutos;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException(MENSAGEM_DURACAO_INVALIDA, e);
        }
    }

    // definir tipo do material este metodo prioriza arquivo depois endereco e por ultimo texto
    private String definirTipo(ConteudoDTO conteudo, MultipartFile arquivo) {

        // definir conteudo como arquivo usa esse tipo quando existe um anexo preenchido
        if (arquivo != null && !arquivo.isEmpty()) {
            return "arquivo";
        }

        // definir conteudo como link usa esse tipo quando existe um endereco preenchido
        if (conteudo.getUrl() != null && !conteudo.getUrl().isBlank()) {
            return "link";
        }

        return "texto";
    }
}
