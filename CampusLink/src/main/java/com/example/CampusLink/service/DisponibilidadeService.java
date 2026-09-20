package com.example.CampusLink.service;

import com.example.CampusLink.model.Disponibilidade;
import com.example.CampusLink.repository.DisponibilidadeRepository;
import com.example.CampusLink.dao.usuarioDAO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.io.PrintWriter;
import java.io.StringWriter;

@Service
@RequiredArgsConstructor
public class DisponibilidadeService {

    @Autowired
    private usuarioDAO usuarioDAO;

    // registra as informacoes do service
    private static final Logger logger = LoggerFactory.getLogger(DisponibilidadeService.class);
    private final DisponibilidadeRepository disponibilidadeRepository;

    // salva ou atualiza a disponibilidade do aluno
    public Disponibilidade salvarDisponibilidade(Long idAluno, LocalDate data, Integer horasDisponiveis) {

        try {logger.debug("Buscando disponibilidade existente. alunoId={} data={}", idAluno, data);
            if (logger.isDebugEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "DEBUG", DisponibilidadeService.class.getName(), "salvarDisponibilidade", null,
                            MessageFormatter.arrayFormat("Buscando disponibilidade existente. alunoId={} data={}", new Object[]{idAluno, data}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            // procura uma disponibilidade para o mesmo aluno e data
            Disponibilidade disponibilidade =
                    disponibilidadeRepository
                            .findByIdAlunoAndData(idAluno, data)
                            .orElseGet(() -> new Disponibilidade(idAluno, data, horasDisponiveis));

            // atualiza a quantidade de horas
            disponibilidade.setHorasDisponiveis(horasDisponiveis);

            // salva a disponibilidade no banco
            Disponibilidade disponibilidadeSalva = disponibilidadeRepository.save(disponibilidade);

            logger.info("Disponibilidade salva. id={} alunoId={} data={} horas={}", disponibilidadeSalva.getId(), idAluno, data, horasDisponiveis);
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", DisponibilidadeService.class.getName(), "salvarDisponibilidade", null,
                            MessageFormatter.arrayFormat("Disponibilidade salva. id={} alunoId={} data={} horas={}", new Object[]{disponibilidadeSalva.getId(), idAluno, data, horasDisponiveis}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return disponibilidadeSalva;

        } catch (DataAccessException e) {
            logger.error("Erro ao salvar disponibilidade. alunoId={} data={}", idAluno, data, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", DisponibilidadeService.class.getName(), "salvarDisponibilidade", null,
                            MessageFormatter.arrayFormat("Erro ao salvar disponibilidade. alunoId={} data={}", new Object[]{idAluno, data}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }
    }

    // busca todas as disponibilidades de um aluno
    public List<Disponibilidade> listarPorAluno(Long idAluno) {

        try {
            List<Disponibilidade> disponibilidades = disponibilidadeRepository.findAllByIdAlunoOrderByDataAsc(idAluno);
            logger.debug("Disponibilidades consultadas. alunoId={} quantidade={}", idAluno, disponibilidades.size());
            if (logger.isDebugEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "DEBUG", DisponibilidadeService.class.getName(), "listarPorAluno", null,
                            MessageFormatter.arrayFormat("Disponibilidades consultadas. alunoId={} quantidade={}", new Object[]{idAluno, disponibilidades.size()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return disponibilidades;

        } catch (DataAccessException e) {
            logger.error("Erro ao consultar disponibilidades. alunoId={}", idAluno, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", DisponibilidadeService.class.getName(), "listarPorAluno", null,
                            MessageFormatter.arrayFormat("Erro ao consultar disponibilidades. alunoId={}", new Object[]{idAluno}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }
    }
}