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
import { CompositeFileSystem } from './composite.fs';
import { InMemoryFileSystemHost, RealFileSystemHost } from '@ts-morph/common';

export type LanguageError = {
  file: string | null;
  start: number,
  message: string,
}

@Injectable()
export class LanguageService {
  project: ts.Project
  languageServer: ts.ts.LanguageService

  private async compileProject(projectF: () => Promise<ts.Project>): Promise<LanguageError[]> {
    const project = await projectF();

    this.project = project;    

    const program = project.createProgram();
    const diagnostics = ts.ts.getPreEmitDiagnostics(program);
    const languageService = project.getLanguageService();

    this.languageServer = languageService;    

    if (diagnostics.length > 0) {
      const cleanDiagnostics = diagnostics.map((d) => {
        return {
          file: d.file?.fileName,
          start: d.start,
          message: (typeof d.messageText === "string") ? d.messageText : d.messageText.messageText,
        };
      });

      return cleanDiagnostics;
      // NOTE: Do not throw. We make best effort
      // throw new BadRequestException('error while compiling');
    } else {
      return [];
    }
  }

  async createProjectFromTSConfig(directories: string[]) {
    return this.compileProject(async () => {

      // if (directories.length === 1) {
      //   return ts.createProject({
      //     tsConfigFilePath: `${directories[0]}/tsconfig.json`,
      //   });
      // } else {
        // maybe throw a helpful error?
      const fileSystem = new RealFileSystemHost();
      const innerFileSystems = directories.map((d) => {
        return {
          root: d,
          fileSystem,
        }
      });

      // const tsConfigFS = new InMemoryFileSystemHost();
      // await tsConfigFS.writeFileSync("tsconfig.prima.json", JSON.stringify({
      //   extends: "tsconfig.json",
      //   compilerOptions: {
      //     allowJs: true
      //   }
      // }, null, 2))

        // TODO: this is temp until we have a real solution to this
      const composite = new CompositeFileSystem([
        ...innerFileSystems,
        // {
        //   root: '/',
        //   fileSystem: tsConfigFS
        // } 
        // last because we only need to access this once. 
        // take single larger performance penalty vs. need to scroll thru every time
        // also presumably should not override
      ]);

      return ts.createProject({
        tsConfigFilePath: '/tsconfig.json',
        fileSystem: composite,
      });
    })
  }

  async createInMemoryProject(files: {[k: string]: string})  {
    return this.compileProject(async () => {
      const project = await ts.createProject({
        useInMemoryFileSystem: true,
        compilerOptions: {
          target: ts.ts.ScriptTarget.ES2016,
        },
      });
  
      for (const k of Object.keys(files)) {
        const v = files[k];
        project.createSourceFile(k, v);
      }

      return project;
    })
  }

  isEmpty() {
    return !this.languageServer
  }

  getDefinition(filename: string, location: number) {
    if (!this.languageServer) {
      throw new BadRequestException('language service not initialized')
    }

    try {

      if(filename.endsWith(".js")) {
        console.warn('skipped')
        return {
          definition: [],
          typeDefinition: [],
        }
      }
      else {
        const definition = this.languageServer.getDefinitionAtPosition(filename, location);
        const typeDefinition = this.languageServer.getTypeDefinitionAtPosition(filename, location) || []

        console.warn(filename, location);
        console.warn(definition);
        return {
          definition,
          typeDefinition
        }
      }
    } catch(e) {
      // TODO: need to handle errors
      console.warn(filename, location);
      console.warn(JSON.stringify(e.ProgramFiles, null, 2))
      console.warn(e);
      throw e;
      // return {
      //   message: e.message,
      //   data: e,
      // };
    }
  }
}
