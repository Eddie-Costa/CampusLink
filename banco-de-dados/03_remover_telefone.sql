-- remocao do telefone dos usuarios
--
-- o telefone nao era usado em nenhuma funcionalidade do campuslink, entao deixa de ser coletado
-- e guardado (minimizacao de dados da LGPD). apagar a coluna tambem apaga o indice unico usuario_telefone_key
--
-- como usar: publique antes a versao do sistema que nao usa mais o telefone e depois cole este
-- script no SQL Editor do Supabase e execute. rodar antes faria o cadastro da versao antiga falhar
-- os telefones ja gravados sao apagados e nao podem ser recuperados

BEGIN;

ALTER TABLE public."USUARIOS" DROP COLUMN IF EXISTS telefone;

COMMIT;

-- conferencia: deve voltar zero linhas
SELECT column_name
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'USUARIOS' AND column_name = 'telefone';
