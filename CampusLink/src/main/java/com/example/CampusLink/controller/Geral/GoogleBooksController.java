package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.GoogleBookDTO;
import com.example.CampusLink.service.GoogleBooksService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.List;

@RestController
public class GoogleBooksController {

    private final GoogleBooksService googleBooksService;

    public GoogleBooksController(GoogleBooksService googleBooksService) {
        this.googleBooksService = googleBooksService;
    }
    @Operation(
            tags = {"Google Books"},
            summary = "Buscar livros",
            description = """
                            Consulta a Google Books API por meio do endpoint
                            GET https://www.googleapis.com/books/v1/volumes.
                    
                            O backend envia o termo q, solicita até 10 resultados
                            e utiliza a chave configurada em GOOGLE_BOOKS_API_KEY.
                    
                            Retorna informações bibliográficas para ajudar o professor
                            a selecionar materiais acadêmicos para a turma.
                            A busca não salva conteúdos automaticamente.
                    
                            Para consultar a API externa, a sessão deve conter usuarioLogado.
                            Sem usuário logado, a implementação atual retorna HTTP 200
                            com uma lista vazia, sem consultar o Google Books.
                            ""\",
        """,
            parameters = {
                    @Parameter(
                            name = "q",
                            in = ParameterIn.QUERY,
                            required = true,
                            description = "Termo utilizado na busca de livros",
                            example = "engenharia de software"
                    )
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de até 10 livros. Pode retornar uma lista vazia quando não há resultados úteis ou usuário logado.",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(
                                    schema = @Schema(
                                            implementation = GoogleBookDTO.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O parâmetro obrigatório q não foi enviado. O controller não possui validação específica para texto vazio.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    type = "object",
                                    description = "Resposta padrão de erro do Spring Boot, conforme a configuração da aplicação."
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Falha não tratada ao consultar o Google Books ou processar sua resposta. O status do provedor não é repassado diretamente.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    type = "object",
                                    description = "Resposta padrão de erro da aplicação; não há um DTO de erro específico desta integração."
                            )
                    )
            )
    })
    @GetMapping("/googlebooks/buscar")
    @ResponseBody
    public List<GoogleBookDTO> buscar(
            @RequestParam("q") String termo,
            HttpSession session) {

        if (session.getAttribute("usuarioLogado") == null) {
            return List.of();
        }

        return googleBooksService.buscarLivros(termo);
    }
}
