import {
  BadRequestException,
  CACHE_MANAGER,
  Inject,
  Injectable,
} from '@nestjs/common';

import { parse } from '@typescript-eslint/typescript-estree';
import * as ts from '@ts-morph/bootstrap';
import { Cache } from 'cache-manager';

type LanguageCache = {
  // project: ts.Project;
  fileSystem: ts.FileSystemHost,
  program: ts.ts.Program;
  languageService: ts.ts.LanguageService;
};

let languageCache = {

}

@Injectable()
export class LanguageService {
  constructor(@Inject(CACHE_MANAGER) private cacheManager: Cache) {}

  analyze(text: string): any {
    return parse(text, { loc: true, range: true, jsx: true });
  }

  async startInMemoryLanguageServer(
    id: string,
    files: { [k: string]: string },
  ) {
    if (await this.cacheManager.get(id)) {
      throw new BadRequestException('id already exists');
    }

    const project = await ts.createProject({
      useInMemoryFileSystem: true,
      compilerOptions: {
        target: ts.ts.ScriptTarget.ES2016,
      },
    });

    for (const key of Object.keys(files)) {
      const value = files[key];

      project.createSourceFile(key, value);
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

    console.warn(
      languageService.getDefinitionAndBoundSpan('test.ts', 169)
    );

    // await this.cacheManager.set<LanguageCache>(id, {
    //   // project,
    //   fileSystem: project.fileSystem,
    //   program,
    //   languageService,
    // });

    languageCache[id] = {
      project,
      languageService
    }
  }

  async stopLanguageServer(id: string) {
    delete languageCache[id];
  }

  async languageServerRequest(id: string, filename: string, location: number) {
    const cache = languageCache[id]

    if (!cache) {
      throw new BadRequestException('language server not found');
    }

    const languageService = cache.project.getLanguageService();

    return languageService.getDefinitionAndBoundSpan(filename, location);
  }
}
