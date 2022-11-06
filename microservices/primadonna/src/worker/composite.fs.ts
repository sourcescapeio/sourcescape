import { NotImplementedException } from '@nestjs/common';
import { FileSystemHost, errors, FileUtils, RuntimeDirEntry } from '@ts-morph/common';

type FileSystemRegistration = {
  root: string,
  fileSystem: FileSystemHost,
}

export class CompositeFileSystem implements FileSystemHost {

  constructor(private readonly fileSystems: FileSystemRegistration[]) {
    if(!fileSystems.length) {
      throw new Error("Need to have at least one fileSystem")
    }
  }

  private doForFile<T>(path: string, f: (_: string, __: FileSystemHost) => T) {
    console.warn('FILE', path)
    let result: T
    for (const fileSystem of this.fileSystems) {
      const mappedPath = `${fileSystem.root}/${path}`;
      if(fileSystem.fileSystem.fileExistsSync(mappedPath)) {
        result = f(mappedPath, fileSystem.fileSystem)
        break;
      }
    }

    const standardizedPath = FileUtils.getStandardizedAbsolutePath(this, path);

    if (!result) {
      throw new errors.FileNotFoundError(standardizedPath)
    } else {
      return result;
    }
  }

  isCaseSensitive(): boolean {
    return this.fileSystems[0].fileSystem.isCaseSensitive();
  }

  /** Asynchronously deletes the specified file or directory. */
  delete(path: string): Promise<void> {
    throw new NotImplementedException("Cannot delete");
  }
  /** Synchronously deletes the specified file or directory */
  deleteSync(path: string): void {
    throw new NotImplementedException("Cannot delete");
  }

  /**
   * Reads all the child directories and files.
   * @remarks Implementers should have this return the full file path.
   */
  readDirSync(dirPath: string): RuntimeDirEntry[] {
    // need to do a remap + need to read entire directory
    const trueDirs = this.fileSystems.map(({root, fileSystem}) => {
      const mappedPath = `${root}/${dirPath}`;
      if(fileSystem.directoryExistsSync(mappedPath)) {
        return fileSystem.readDirSync(mappedPath).map((entry) => ({
          ...entry,
          name: entry.name.replace(root, '/')
        }));
      } else {
        return [];
      }
    });

    return trueDirs.reduce((accumulator, value) => accumulator.concat(value), []);
  }

  /** Asynchronously reads a file at the specified path. */
  readFile(filePath: string, encoding?: string): Promise<string> {
    return this.doForFile(filePath, async (mappedPath, fileSystem) => {
      console.warn(filePath, mappedPath)
      return fileSystem.readFile(mappedPath, encoding);
    });
  }
  /** Synchronously reads a file at the specified path. */
  readFileSync(filePath: string, encoding?: string): string {
    return this.doForFile(filePath, (mappedPath, fileSystem) => {
      // console.warn('SYNC', filePath, mappedPath)
      return fileSystem.readFileSync(mappedPath, encoding);
    });
  }

  /** Asynchronously writes a file to the file system. */
  writeFile(filePath: string, fileText: string): Promise<void> {
    throw new NotImplementedException("Cannot write");
  }

  /** Synchronously writes a file to the file system. */
  writeFileSync(filePath: string, fileText: string): void {
    throw new NotImplementedException("Cannot write");
  }

  /** Asynchronously creates a directory at the specified path. */
  mkdir(dirPath: string): Promise<void> {
    throw new NotImplementedException("Cannot mkdir");
  }

  /** Synchronously creates a directory at the specified path. */
  mkdirSync(dirPath: string): void {
    throw new NotImplementedException("Cannot mkdir");
  }

  /** Asynchronously moves a file or directory. */
  move(srcPath: string, destPath: string): Promise<void> {
    throw new NotImplementedException("Cannot move");
  }

  /** Synchronously moves a file or directory. */
  moveSync(srcPath: string, destPath: string): void {
    throw new NotImplementedException("Cannot move");
  }

  /** Asynchronously copies a file or directory. */
  copy(srcPath: string, destPath: string): Promise<void> {
    throw new NotImplementedException("Cannot copy");
  }

  /** Synchronously copies a file or directory. */
  copySync(srcPath: string, destPath: string): void {
    throw new NotImplementedException("Cannot copy");
  }
  
  /** Asynchronously checks if a file exists.
   * @remarks Implementers should throw an `errors.FileNotFoundError` when it does not exist.
   */
  fileExists(filePath: string): Promise<boolean> {
    return Promise.resolve(this.fileSystems.some(({root, fileSystem}) => {
      const mappedPath = `${root}/${filePath}`;
      return fileSystem.fileExistsSync(mappedPath);
    }));
  }

  /** Synchronously checks if a file exists.
   * @remarks Implementers should throw an `errors.FileNotFoundError` when it does not exist.
   */
  fileExistsSync(filePath: string): boolean {
    return this.fileSystems.some(({root, fileSystem}) => {
      const mappedPath = `${root}/${filePath}`;
      return fileSystem.fileExistsSync(mappedPath);
    });
  }

  /** Asynchronously checks if a directory exists. */
  async directoryExists(dirPath: string): Promise<boolean> {
    const work = this.fileSystems.map(async ({root, fileSystem}) => {
      return fileSystem.directoryExists(dirPath)
    });

    const vv = await Promise.all(work);
    return vv.some(i => i)
  }

  /** Synchronously checks if a directory exists. */
  directoryExistsSync(dirPath: string): boolean {
    this.fileSystems.forEach(({root, fileSystem}) => {
      if(fileSystem.directoryExistsSync(dirPath)) {
        return true;
      }
    });

    return false;
  }

  /** See https://nodejs.org/api/fs.html#fs_fs_realpathsync_path_options */
  realpathSync(path: string): string {
    throw new NotImplementedException("not impl");
  }

  /** Gets the current directory of the environment. */
  getCurrentDirectory(): string {
    return '/'
  }
  /** Uses pattern matching to find files or directories. */
  glob(patterns: ReadonlyArray<string>): Promise<string[]> {
    throw new NotImplementedException("not impl");
  }
  /** Synchronously uses pattern matching to find files or directories. */
  globSync(patterns: ReadonlyArray<string>): string[] {
    throw new NotImplementedException("not impl");
  }
}
