start.txt - plik z parametrami, niezbędny do uruchomienia projektu
W celu poprawnego uruchomienia należy zmienić CACHE_DIR - ścieżkę do plików cache. Nie może ona się kończyć backslash'em.

start.bat - skrypt uruchamiający całe proxy(plik jar za pomocą polecenia java); skrypt odpala proxy.jar z argumentem "start.txt"

Nie zostało zaimplementowane:
- obsługa połączeń https
- obsługa encodowania response - np. gzip
- oznaczenie stron pochodzących z cache

Kod źródłowy (pliki projektu z IntelliJ włącznie z plikami z kodem) znajduje się w folderze source; package to skj_proxy.