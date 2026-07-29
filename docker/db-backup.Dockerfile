FROM mysql:8.4

COPY docker/db-backup-entrypoint.sh /usr/local/bin/db-backup-entrypoint.sh
RUN chmod +x /usr/local/bin/db-backup-entrypoint.sh

ENTRYPOINT ["/usr/local/bin/db-backup-entrypoint.sh"]
