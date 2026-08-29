- Melhorar problem detail para ter erro usuario e erro tecnico
- Testar whatsapp evolution e oficial 
- Adicionar 2f por whatsapp 
- bloquear temp email 
- tratar ratelimit em rotas publicas e de api (se for o caso retomar o resilience)
- criar artifact e ci/cd no github das libs 
- Cliente informou o email incorreto e clicou para criar uma conta, como proceder ?
- sem acesso ao logs (401)
- nao permitir subdominios no multitenancy
	panel
admin
ops
infra
sys
api
auth
www
mail
smtp
imap
pop
cdn
static
assets

nginx para redirect ao produto com mesmo dominio 

colocar um  refreshtoken  - https://chatgpt.com/share/69aff46f-89ac-8010-ac77-a3a99f437424

cliente cadastrou com o email errado e por isso o email nao chega 



no DNS   
Fica com 4 registros principais no DNS da zona seedgrid.com.br (sem criar por tenant):

A secret -> 161.97.102.229
A *.secret -> 161.97.102.229
A *.dev.secret -> 161.97.102.229
A *.hml.secret -> 161.97.102.229


Config por ambiente:

prod: DOMAIN_SUFFIX=secret.seedgrid.com.br
dev: DOMAIN_SUFFIX=dev.secret.seedgrid.com.br
hml: DOMAIN_SUFFIX=hml.secret.seedgrid.com.br