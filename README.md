# CentralizeSys - Sistema de Gestión para Comercios

**Sistema de Punto de Venta e Inventario** diseñado para usuarios de edad avanzada, con interfaz de alto contraste y tipografía grande siguiendo estándares WCAG AAA.

Este documento proporciona todo lo necesario para iniciar y ejecutar el proyecto **desde cero** en una computadora nueva, paso a paso, tanto para entornos **Windows** como **Ubuntu**.

---

## 🚀 Instalación desde Cero (De 0 a 100%)

Seleccione su Sistema Operativo para ver la guía de instalación paso a paso. Se asume que la computadora está limpia y no tiene herramientas previas instaladas.

### 🪟 Guía de Configuración para Windows

**Paso 1: Instalar Git**
Git es la herramienta necesaria para descargar el código fuente.
1. Descargue el instalador de Git desde: https://git-scm.com/download/win
2. Ejecute el instalador `.exe` descargado (las opciones por defecto del instalador están bien, simplemente presione "Siguiente" hasta finalizar).
3. **Verificación:** Abra una nueva ventana de PowerShell (Búsqueda de Windows -> "PowerShell") y escriba:
   ```powershell
   git --version
   # Debe mostrar algo como: git version 2.4x.x.windows.1
   ```

**Paso 2: Instalar Java (OpenJDK 21 - LTS)**
Java es necesario para compilar y ejecutar el servidor Backend (La lógica y base de datos).
1. Descargue el instalador `.msi` de OpenJDK 21 desde Adoptium: https://adoptium.net/temurin/releases/?version=21 (Asegúrese de elegir el de Windows x64 .msi).
2. Ejecute el instalador `.msi`. **Muy Importante:** Durante la instalación, en la pantalla de "Custom Setup" (Configuración Personalizada), haga clic en la "X" roja al lado de **"Set JAVA_HOME variable"** y cámbiela a "Will be installed on local hard drive" (Se instalará en el disco duro local).
3. **Verificación:** Abra una **NUEVA** ventana de PowerShell (cierre la anterior para que Windows cargue las nuevas variables) y escriba:
   ```powershell
   java -version
   # Debe mostrar: openjdk version "21.0.x"
   ```

**Paso 3: Instalar Node.js (v20 o superior)**
Node.js es necesario para descargar las librerías, compilar y ejecutar la interfaz visual (Frontend).
1. Descargue el instalador `.msi` (Versión LTS recomendada) desde: https://nodejs.org/es
2. Ejecute el instalador y siga las instrucciones (las opciones por defecto son correctas).
3. **Verificación:** Abra PowerShell y escriba:
   ```powershell
   node --version
   # Debe mostrar: v20.x.x o superior
   npm --version
   # Debe mostrar: 10.x.x o superior
   ```

**Paso 4: Descargar el Proyecto y Ejecutar**
Con las herramientas instaladas, ahora descargaremos y encenderemos el sistema.
1. Abra PowerShell, navegue a la carpeta donde desea guardar el proyecto (por ejemplo, su carpeta de Documentos) y descargue el código:
   ```powershell
   cd ~/Documents
   git clone https://github.com/Valarcos/sinpen_thesis.git
   cd sinpen_thesis
   ```
2. **Encender el Backend (Base de datos y Servidor):** En esa misma ventana de PowerShell, ejecute:
   ```powershell
   cd backend
   # El primer arranque tardará un poco porque descargará dependencias automáticamente usando Gradle.
   ./gradlew bootRun
   ```
   *Nota: Si Windows Defender le pregunta sobre el firewall de Java, haga clic en "Permitir acceso".* El servidor quedará encendido esperando conexiones en la dirección `http://localhost:8080`.
3. **Encender el Frontend (Interfaz Visual):** Abra una **NUEVA** y segunda ventana de PowerShell dejando la anterior abierta. Navegue al proyecto y ejecute:
   ```powershell
   cd ~/Documents/sinpen_thesis/frontend
   npm install
   # Para iniciar la página web:
   npm run dev
   ```
   *¡Listo! La interfaz visual estará disponible y funcionando en su navegador web en la dirección: `http://localhost:5173`*

---

### 🐧 Guía de Configuración para Ubuntu (Linux)

Esta guía asume una instalación fresca de Ubuntu Desktop o Ubuntu Server. Estas instrucciones están diseñadas para copiarse y pegarse directamente en la terminal.

**Paso 1: Actualizar el Sistema**
Asegúrese de que el sistema operativo tenga las librerías básicas actualizadas.
```bash
sudo apt update && sudo apt upgrade -y
```

**Paso 2: Instalar Git**
Git es necesario para descargar el código fuente.
```bash
sudo apt install git -y
# Verificación:
git --version
# Debe mostrar: git version 2.x.x
```

**Paso 3: Instalar Java (OpenJDK 21)**
El backend de Spring Boot requiere explícitamente Java versión 21 (LTS).
```bash
# Ubuntu 24.04+ (Noble Numbat) incluye openjdk-21 en sus repositorios nativos. Pruebe primero:
sudo apt install openjdk-21-jdk -y

# Si ocurre un error porque su Ubuntu es más antiguo, instálelo desde el repositorio oficial de Adoptium:
sudo apt install wget apt-transport-https gnupg -y
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update && sudo apt install temurin-21-jdk -y

# Verificación:
java -version
# Debe mostrar: openjdk version "21.0.x"
```

**Paso 4: Instalar Node.js (v20 LTS)**
La herramienta del frontend (Vite) requiere una versión de Node.js reciente. Usaremos NodeSource para instalar la versión 20.
```bash
# Instalar curl si no lo tiene
sudo apt install curl -y

# Añadir el repositorio oficial de NodeSource para Node.js v20
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -

# Instalar Node.js (Esto incluye 'npm' automáticamente)
sudo apt install -y nodejs

# Verificación:
node --version
# Debe mostrar: v20.x.x
npm --version
# Debe mostrar: 10.x.x o superior
```

**Paso 5: Descargar el Proyecto y Ejecutar**
Con las herramientas instaladas, encenderemos el sistema.
1. Clone (descargue) el repositorio en su carpeta principal:
   ```bash
   cd ~
   git clone https://github.com/Valarcos/sinpen_thesis.git
   cd sinpen_thesis
   ```
2. **Encender el Backend (Base de datos y Servidor):**
   ```bash
   cd backend
   # Dar permisos de ejecución al instalador de Gradle
   chmod +x gradlew
   # Iniciar el servidor (la primera vez descargará librerías de internet y tardará unos minutos)
   ./gradlew bootRun
   ```
   *El servidor quedará encendido en `http://localhost:8080`.*
3. **Encender el Frontend (Interfaz Visual):** Abra una **NUEVA** pestaña o ventana de terminal y déjela abierta junto a la del backend.
   ```bash
   cd ~/sinpen_thesis/frontend
   # Instalar dependencias visuales
   npm install
   # Para iniciar la página web:
   npm run dev
   # ATENCIÓN: Si instaló esto en un Ubuntu Server en la nube (ej. Oracle Cloud) sin monitor, 
   # debe correrlo exponiendo la red para acceder desde internet así:
   # npm run dev -- --host 0.0.0.0
   ```
   *¡Listo! El frontend estará disponible en `http://localhost:5173` (o http://IP-PUBLICA-DEL-SERVIDOR:5173 si usó Ubuntu en la nube y abrió los puertos 8080 y 5173 en su firewall).*

---

## 🔧 Uso y Configuración del Sistema (Ambos Sistemas)

### 👤 Ingresar al Sistema: Usuarios de Prueba
Al iniciar el backend por primera vez, el sistema creará automáticamente la base de datos `data/centralizesys.db` y un usuario administrador principal por defecto:
- **Email**: `marcosachavalmbaj@gmail.com`
- **Contraseña**: (Ya estará encriptada en la base de datos). Ingrese únicamente con el hash vacío si no recuerda la clave local, o recupere los datos de su entorno local pre-existente.
- **Rol**: `ADMIN`

Una vez logueado, podrá dirigirse al módulo de "Administración" (El botón con el candado amarillo en el Dashboard) para crear a sus propios cajeros, repositores y nuevos administradores desde la interfaz.

### 🔌 Conectar el Frontend a un Servidor (Nube o Red Wi-Fi local)
Si el Backend (La base de datos del paso 2) está corriendo en una computadora distinta a la que abre el navegador (Por ejemplo, en un Ubuntu Cloud, o en otra PC de su local conectada al Wi-Fi):

Cree un archivo llamado `.env` dentro de la carpeta `frontend/`:
```env
# Reemplace la URL de ejemplo con la IP real donde corrió el paso 2
VITE_BACKEND_URL=http://192.168.1.15:8080
```
Guarde el archivo y reinicie el comando `npm run dev`. Ahora la interfaz gráfica sabrá dónde buscar su base de datos remota.

---

## 🌐 Despliegue Público sin abrir puertos (Cloudflare Tunnel)

Si usted tiene el sistema corriendo en una PC de su local y quiere acceder a él desde su casa **sin configurar su módem ni pagar VPS**, esta es la mejor solución gratuita.

Cree **dos túneles**: uno para el backend y otro para el frontend.

1. **Instalar Cloudflare Tunnel:**
   - **Windows:** Ejecute en PowerShell: `winget install --id Cloudflare.cloudflared`
   - **Ubuntu:** `curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb -o cloudflared.deb && sudo dpkg -i cloudflared.deb`
2. Con su Backend Encendido (en el puerto 8080), abra una nueva terminal y escriba:
   ```bash
   cloudflared tunnel --url http://localhost:8080
   ```
3. La consola le dará una URL como: `https://palabras-random-backend.trycloudflare.com`. Cópiela.
4. Vaya a su carpeta `frontend/`, abra/cree el archivo `.env` y ponga esa ruta:
   ```env
   VITE_BACKEND_URL=https://palabras-random-backend.trycloudflare.com
   ```
5. Encienda su Frontend (`npm run dev` en el puerto 5173) y abra otra terminal para hacer el segundo túnel:
   ```bash
   cloudflared tunnel --url http://localhost:5173
   ```
6. **¡Listo!** Envíe el segundo link generado por Cloudflare a su celular o PC remota. Todo el sistema correrá por internet encriptado hacia la base de datos de su negocio.

## ☁️ DevOps y Operaciones en Producción (Cloud)

El despliegue del sistema en servidores en la nube (Google Cloud, Oracle Cloud, VPS) requiere consideraciones críticas de infraestructura para evitar pérdida de datos o caídas de servicio. A continuación se detallan las configuraciones indispensables y un registro de resolución de problemas históricos.

### 🛑 Advertencia Crítica sobre Direcciones IP y DNS (Donweb)
Si usted apaga su servidor virtual (Instancia) en la nube para ahorrar costos, el proveedor (Google u Oracle) le quitará su Dirección IP Pública y le asignará una nueva al volver a encenderlo.
- **Efecto:** Su dominio (ej. `mariaclosys.com.ar`) en su proveedor de DNS (como Donweb o Cloudflare) seguirá apuntando a la IP vieja, dejando su sistema completamente inaccesible ("Página no encontrada").
- **Solución Obligatoria:** Debe reservar una **IP Pública Estática** (Reserved IP) en el panel de su proveedor de nube y asignarla a su máquina. Si no desea pagar el recargo por IP Estática (que se cobra incluso si la máquina está apagada), **no apague el servidor** o prepárese para entrar a Donweb diariamente a actualizar el Registro "A" de su dominio.

### 🛡️ Prevención de Errores Comunes de Despliegue (Lecciones Aprendidas)
1. **Pérdida de Backups en Docker:** La base de datos guarda los respaldos SQL en `/app/data`. Si el archivo `docker-compose-prod.yml` carece del mapeo de volumen `backend_data:/app/data`, **todos sus respaldos se borrarán permanentemente** cada vez que actualice el contenedor del backend. Asegúrese de que este volumen siempre esté definido.
2. **Caché Persistente del Navegador (Error 503 tras actualizar):** Cuando despliega una nueva versión de la interfaz visual (Frontend), los navegadores de sus clientes mantendrán el código Javascript antiguo en su memoria caché de manera muy agresiva. Esto genera errores de ruta (ej. requests a `/api/api/`) si se modificaron. Tras actualizar el código en el servidor, **siempre indique a los usuarios realizar un Hard Refresh (Ctrl+F5)** o limpiar la caché de su navegador.
3. **Auditoría de IPs Falsas tras Proxy:** Debido a que el sistema opera detrás de Caddy (Reverse Proxy), el backend registrará todos los intentos de inicio de sesión como provenientes de la IP de Caddy (ej. `172.18.0.x`), inutilizando los bloqueos por fuerza bruta. Esto se soluciona asegurando que la variable `SERVER_FORWARD_HEADERS_STRATEGY=native` esté presente en el bloque del `backend` en `docker-compose-prod.yml`.

---

## 🚀 Guía de Migración hacia Oracle Cloud (Arquitectura ARM64)

Si decide abandonar Google Cloud Platform y aprovechar la capa gratuita "Pay-as-you-go" de Oracle Cloud (Instancia Ampere A1), debe seguir estrictamente estos pasos para evitar los errores de arquitectura más comunes.
*Atención: La cuota gratuita de Oracle se redujo recientemente de 24 GB de RAM a 12 GB, lo que refuerza la necesidad de gestionar la memoria de Java cuidadosamente.*

**Paso 1: Respaldo Previo a la Migración**
Antes de apagar su instancia de Google Cloud, descargue el último archivo `.sql` de la base de datos (puede hacerlo desde el panel de Respaldos del sistema o utilizando `pg_dump` manualmente en el servidor). Descárguelo a su computadora personal.

**Paso 2: Creación de la Instancia en Oracle**
1. Cree una instancia **Ampere A1 (Arquitectura ARM64)** con Oracle Linux o Ubuntu. Asigne al menos 2 OCPUs y 12 GB de RAM.
2. **Seguridad de Red (VCN):** En Oracle, no basta con el firewall interno de Ubuntu. Debe ingresar a la Virtual Cloud Network (VCN) -> Security Lists -> Default Security List, y agregar reglas de entrada (Ingress Rules) para los puertos **80 (HTTP)** y **443 (HTTPS)** permitiendo el tráfico desde `0.0.0.0/0`.
3. Reserve una IP Pública Estática en Oracle y actualice los DNS de Donweb para que apunten a esta nueva IP.

**Paso 3: Clonación y Construcción Específica para ARM64**
Debido a que el procesador de Oracle (Ampere) no es el clásico Intel/AMD (x86_64) que usa Google Cloud, **no intente migrar las imágenes Docker pre-compiladas**.
1. Instale Git y Docker en la instancia de Oracle.
2. Clone el repositorio: `git clone https://github.com/Valarcos/sinpen_thesis.git`
3. Ingrese a la carpeta del proyecto. Verifique que `docker-compose-prod.yml` contiene la variable:
   `JAVA_TOOL_OPTIONS=-Xms512m -Xmx1g`
   *(Esto es crítico: evita que Java intente consumir los 12GB de RAM enteros, lo cual invocaría al "OOM Killer" del sistema operativo y tumbaría el servidor).*
4. Reconstruya el sistema desde el código fuente directamente en el procesador ARM de Oracle:
   ```bash
   docker compose -f docker-compose-prod.yml build --no-cache
   docker compose -f docker-compose-prod.yml up -d
   ```

**Paso 4: Restauración de Base de Datos**
1. Transfiera su archivo `.sql` de respaldo (el que bajó en el Paso 1) al servidor de Oracle.
2. Inyéctelo en la base de datos limpia de PostgreSQL corriendo el siguiente comando:
   ```bash
   docker exec -i centralizesys_db psql -U su_usuario -d su_base_de_datos < /ruta/al/backup.sql
   ```
   *(Nota: Asegúrese de que el nombre del contenedor `centralizesys_db` coincida exactamente con el de su archivo compose).*

**Paso 5: Limpieza de Caché**
Abra `https://mariaclosys.com.ar` y presione **Ctrl+F5**. El sistema ya debería estar operando desde Oracle Cloud sin pérdida de datos.

---

## 📝 Stack y Tecnologías

El repositorio actual (Sprint 7 avanzado) contiene las siguientes tecnologías operando en conjunto:
*   **Backend:** Java 21 LTS, Spring Boot 3.3.3, Base de Datos SQLite (Embedded), Seguridad Web con Spring Security + Tokens JWT. Compilado con Gradle (Kotlin DSL).
*   **Frontend:** Interfaz en React 18, empaquetado ultra-rápido con Vite 6, Manejo de URLs con React Router v7, llamadas de red con Axios y alertas flotantes con React Hot Toast. Archivos PDF de tickets generados en el navegador puro usando jsPDF y AutoTable.
*   **Diseño (UX/UI):** CSS puro Vainilla hecho a medida sin librerías pesadas como Bootstrap/Tailwind. Elementos ultra-espaciados, contraste mejorado, bloqueos de doble click (isSubmitting protections) y adaptabilidad responsiva diseñada para evitar toques fantasmas en tablets móviles.

## 📧 Acerca de y Contacto
Proyecto final de Tesis Universitaria.
**Desarrollador**: Marcos Achaval
**Email**: marcosachavalmbaj@gmail.com
**GitHub**: [Valarcos](https://github.com/Valarcos)

---

## ⚠️ Known risks/possible dev problems

1. **The Docker Exec PATH Concession:**
   Local development relies on `docker exec centralizesys_postgres` to run `pg_dump` and `psql` within the Java `BackupService`. Si el nombre del contenedor de la base de datos cambia en el `docker-compose`, el código Java (`BackupService.java`) debe ser actualizado manualmente para reflejar este nuevo nombre.
2. **Automated Schema Wipe (The former Hybrid Schema Risk):**
   Restaurar un respaldo antiguo sobre una versión nueva de la aplicación ahora es completamente automático. El sistema realiza una inyección a nivel de S.O. (`psql`) que fuerza la caída de todas las conexiones activas y ejecuta un `DROP SCHEMA public CASCADE` antes de restaurar, garantizando cero conflictos de llaves foráneas y recreando mágicamente cualquier tabla nueva en el arranque de Spring. (Se superaron los deadlocks pasados eliminando a Java como intermediario).
3. **The Single Transaction Flag:**
   El proceso de restauración utiliza `--single-transaction`. Es perfectamente seguro para la base de datos actual (pequeña), pero si el volumen de datos escala masivamente en el futuro, inyectar una reconstrucción completa en una sola transacción puede causar agotamiento de memoria del *Write-Ahead Log (WAL)* en PostgreSQL.
4. **Lack of Remote Logger in Frontend:**
   Actualmente el frontend (React) utiliza console.log y console.error para el manejo de excepciones y flujos de información en producción, lo que puede exponer lógica interna y ensuciar la consola del navegador del cliente. Se registra como deuda técnica la implementación de un logger remoto (ej. Sentry, Datadog o un endpoint propio) para remover estos logs crudos del entorno productivo, cumpliendo estrictamente con las normativas definidas en .cursorrules.
5. **Architectural Coupling & "God Services" (Technical Debt):**
   Debido a la naturaleza monolítica del sistema, existe un fuerte acoplamiento de dominios (Domain Coupling). Servicios como `VentaService` actúan como "God Services" que orquestan e invocan directamente repositorios de otros módulos (`DeudoresRepository`, `AlertaChequeRepository`, `StockRepository`). Esto provoca que cualquier cambio en la estructura de ventas (ej. agregar `saldo_generado`) exija modificaciones en cascada en toda la aplicación, forzando despliegues de "todo o nada".
   - *Solución a Futuro (Backend):* Implementar Arquitectura Orientada a Eventos Internos (Event-Driven) usando `ApplicationEventPublisher` de Spring. `VentaService` debe emitir eventos (ej. `SaleCompletedEvent`) que otros módulos escuchen para actualizar sus estados (inventario, deudas) de forma desacoplada.
   - *Solución a Futuro (Frontend):* Dividir componentes "God" como `VentaPage.jsx` (actualmente +1600 líneas) separando la lógica de estado del carrito, el flujo de checkout, y la renderización de modales en componentes más pequeños y mantenibles; además de utilizar DTOs hidratados desde el backend para evitar que el frontend intente reconstruir relaciones complejas de la base de datos a mano.
```
