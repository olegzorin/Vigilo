-- Create the vf database with UTF-8 encoding.
--
-- PostgreSQL fixes the encoding at CREATE DATABASE time. Run this as a superuser before applying
-- the DDL scripts in postgresql/ddl/.
--
-- template0 is a PostgreSQL system template database, used so an explicit
-- ENCODING / locale can be chosen regardless of the template1 defaults.

CREATE DATABASE vf WITH
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'
    LC_CTYPE = 'en_US.UTF-8'
    TEMPLATE = 'template0';
