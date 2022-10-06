import {
  BadRequestException,
  CACHE_MANAGER,
  Inject,
  Injectable,
  InternalServerErrorException,
} from '@nestjs/common';

import { parse } from '@typescript-eslint/typescript-estree';
import * as ts from '@ts-morph/bootstrap';
import { Cache } from 'cache-manager';
import { ChildProcess, spawn } from 'child_process';
import { ClientProxyFactory, Transport } from '@nestjs/microservices';
import * as process from 'process';
import { firstValueFrom, scan } from 'rxjs';

@Injectable()
export class LanguageService {
  project: ts.Project
  languageServer: ts.ts.LanguageService

  async createProjectFromTSConfig(directory: string) {
    const project = await ts.createProject({
      tsConfigFilePath: directory,
    });

    this.project = project;

    const program = project.createProgram();
    const diagnostics = ts.ts.getPreEmitDiagnostics(program);
    const languageService = project.getLanguageService();

    if (diagnostics.length > 0) {
      const cleanDiagnostics = diagnostics.map((d) => {
        return {
          file: d.file?.fileName,
          start: d.start,
          message: d.messageText,
        };
      });
      console.error(cleanDiagnostics);
      // NOTE: Do not throw. We make best effort
      // throw new BadRequestException('error while compiling');
    }

    this.languageServer = languageService;
  }

  async createInMemoryProject(files: {[k: string]: string})  {
    const project = await ts.createProject({
      useInMemoryFileSystem: true,
      compilerOptions: {
        target: ts.ts.ScriptTarget.ES2016,
      },
    });

    this.project = project;

    for (const k of Object.keys(files)) {
      const v = files[k];
      project.createSourceFile(k, v);
    }

    const program = project.createProgram();
    const diagnostics = ts.ts.getPreEmitDiagnostics(program);
    const languageService = project.getLanguageService();

    if (diagnostics.length > 0) {
      const cleanDiagnostics = diagnostics.map((d) => {
        return {
          file: d.file.fileName,
          start: d.start,
          message: d.messageText,
        };
      });
      throw new BadRequestException(cleanDiagnostics, 'error while compiling');
    }

    this.languageServer = languageService;
  }

  isEmpty() {
    return !this.languageServer
  }

  getDefinition(filename: string, location: number) {
    if (!this.languageServer) {
      throw new BadRequestException('language service not initialized')
    }

    try {
      return {
        definition: this.languageServer.getDefinitionAtPosition(filename, location),
        typeDefinition: this.languageServer.getTypeDefinitionAtPosition(filename, location)
      }
    } catch(e) {
      // TODO: need to handle errors
      throw e
      // return {
      //   message: e.message,
      //   data: e,
      // };
    }
  }
}
